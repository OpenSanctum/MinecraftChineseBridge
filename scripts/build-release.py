#!/usr/bin/env python3
import argparse
import datetime
import json
import os
import platform
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MATRIX_PATH = ROOT / '.github' / 'release-matrix.json'
GRADLE_PROPS_PATH = ROOT / 'gradle.properties'
DIST_DIR = ROOT / 'dist'


def read_release_matrix():
    with MATRIX_PATH.open('r', encoding='utf-8') as fh:
        return json.load(fh)


def read_current_version():
    text = GRADLE_PROPS_PATH.read_text(encoding='utf-8')
    match = re.search(r'^mod_version\s*=\s*(\d+)', text, re.M)
    if not match:
        raise RuntimeError('mod_version not found in gradle.properties')
    return match.group(1)


def generate_date_version():
    return datetime.datetime.now(datetime.UTC).strftime('%y%m%d')


def write_version(version):
    current = read_current_version()
    text = GRADLE_PROPS_PATH.read_text(encoding='utf-8')
    text = re.sub(r'(?m)^mod_version\s*=.*$', f'mod_version={version}', text, count=1)
    GRADLE_PROPS_PATH.write_text(text, encoding='utf-8')
    print(f'Updated version from {current} to {version}')
    return version


def ensure_java():
    java_home = os.environ.get('JAVA_HOME')
    if java_home and os.path.exists(java_home):
        return java_home

    if platform.system() == 'Windows':
        candidates = [
            r'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.8-hotspot',
            r'C:\Program Files\Java\jdk-21',
            r'C:\Program Files\Java\jdk-17',
        ]
    else:
        candidates = [
            '/Library/Java/JavaVirtualMachines',
            '/usr/lib/jvm',
        ]

    for candidate in candidates:
        if os.path.isdir(candidate):
            if platform.system() == 'Windows':
                return candidate
            # macOS / Linux: look for a Java home inside the directory
            for child in sorted(Path(candidate).glob('*')):
                if child.is_dir() and (child / 'bin' / 'java').exists():
                    return str(child)

    raise RuntimeError('Java 21 not found. Please install Temurin 21 JDK and set JAVA_HOME.')


def run_command(cmd, cwd=None, env=None):
    print('> ' + ' '.join(cmd))
    subprocess.run(cmd, cwd=cwd, env=env, check=True)


def stop_gradle_daemons(project_dir, env=None):
    gradlew_name = 'gradlew.bat' if platform.system() == 'Windows' else 'gradlew'
    gradlew = str(project_dir / gradlew_name)
    try:
        run_command([gradlew, '--stop'], cwd=project_dir, env=env)
    except subprocess.CalledProcessError:
        # Do not fail release builds just because no daemon was running.
        pass


def clean_lock_prone_outputs(project_dir):
    if platform.system() == 'Windows':
        command = (
            "Remove-Item -Recurse -Force .\\build\\classes\\java\\main -ErrorAction SilentlyContinue; "
            "Remove-Item -Recurse -Force .\\build\\resources\\client -ErrorAction SilentlyContinue; "
            "Remove-Item -Recurse -Force .\\build\\resources\\main -ErrorAction SilentlyContinue"
        )
        subprocess.run(
            ['powershell', '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', command],
            cwd=project_dir,
            check=False,
        )
        return

    build_dir = project_dir / 'build'
    candidates = [
        build_dir / 'classes' / 'java' / 'main',
        build_dir / 'resources' / 'client',
        build_dir / 'resources' / 'main',
    ]
    for target in candidates:
        shutil.rmtree(target, ignore_errors=True)


def build_loader(loader, version, project_dir):
    gradlew_name = 'gradlew.bat' if platform.system() == 'Windows' else 'gradlew'
    gradlew = str(project_dir / gradlew_name)
    cmd = [gradlew, '--no-daemon', '--max-workers=1', '-Pmod_version=' + version, 'assemble']
    env = os.environ.copy()
    java_home = ensure_java()
    env['JAVA_HOME'] = java_home
    if platform.system() == 'Windows':
        env['Path'] = java_home + r'\bin;' + env.get('Path', '')
    else:
        env['PATH'] = str(Path(java_home) / 'bin') + os.pathsep + env.get('PATH', '')

    if platform.system() == 'Windows':
        stop_gradle_daemons(project_dir, env=env)
        clean_lock_prone_outputs(project_dir)

    run_command(cmd, cwd=project_dir, env=env)


def copy_output(project_dir, target_name, loader, version):
    libs_dir = project_dir / 'build' / 'libs'
    jar_candidates = list(libs_dir.rglob('*.jar')) if libs_dir.exists() else []
    jar_candidates = [
        p for p in jar_candidates
        if p.is_file()
        and 'sources' not in p.name.lower()
        and 'javadoc' not in p.name.lower()
        and 'dev' not in p.name.lower()
    ]
    if not jar_candidates:
        raise RuntimeError(f'No jar found in {project_dir}')

    # Forge/NeoForge require the fat jar containing bundled dependencies.
    if loader in {'forge', 'neoforge'}:
        pool = [p for p in jar_candidates if p.name.endswith('-all.jar')]
        if not pool:
            raise RuntimeError(f'No *-all.jar found in {project_dir} for {loader}')
    else:
        pool = [p for p in jar_candidates if not p.name.endswith('-all.jar')] or jar_candidates

    versioned_pool = [p for p in pool if f'-{version}' in p.stem]
    if versioned_pool:
        jar_path = max(versioned_pool, key=lambda path: path.stat().st_mtime)
    else:
        jar_path = max(pool, key=lambda path: path.stat().st_mtime)
        print(f'Warning: no artifact matched version {version}, fallback to latest {jar_path.name}')

    target_path = DIST_DIR / target_name
    shutil.copy2(jar_path, target_path)
    print(f'Created {target_path} <- {jar_path.name}')


def parse_args():
    parser = argparse.ArgumentParser(description='Build release jars locally and optionally upload them to GitHub Releases.')
    parser.add_argument('--version', help='Override the release program/version number (e.g. 260731)')
    parser.add_argument('--loader', choices=['fabric', 'forge', 'neoforge', 'all'], default='all')
    parser.add_argument('--series', help='Only build a specific series (this branch supports 26.2.x only)')
    parser.add_argument('--skip-bump', action='store_true', help='Do not update mod_version in gradle.properties')
    parser.add_argument('--upload', action='store_true', help='Upload generated jars to a GitHub Release')
    parser.add_argument('--release-tag', default='main-builds', help='GitHub Release tag to upload to')
    return parser.parse_args()


def upload_to_github(release_tag, dist_dir):
    if shutil.which('gh') is None:
        raise RuntimeError('GitHub CLI (gh) is required for upload. Install it first.')
    subprocess.run(['gh', 'release', 'view', release_tag], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if subprocess.run(['gh', 'release', 'view', release_tag], check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0:
        subprocess.run(['gh', 'release', 'create', release_tag, '--title', 'Main builds', '--notes', 'Local builds', '--prerelease'], check=True)
    for jar in sorted(dist_dir.glob('*.jar')):
        subprocess.run(['gh', 'release', 'upload', release_tag, str(jar), '--clobber'], check=True)


def build_all(args):
    data = read_release_matrix()
    if args.series and args.series != '26.2.x':
        raise RuntimeError('Only 26.2.x is supported on this branch')

    version = args.version or generate_date_version()
    if not args.skip_bump:
        write_version(version)
    else:
        print(f'Using version {version} without updating gradle.properties')

    DIST_DIR.mkdir(exist_ok=True)

    for release in data['releases']:
        series = release['series']
        if series != '26.2.x':
            continue
        if args.series and series != args.series:
            continue
        for loader in release['loaders']:
            if args.loader != 'all' and loader != args.loader:
                continue
            filename = data['filename'].format(loader=loader, series=series, program=version)
            if loader == 'fabric':
                build_loader(loader, version, ROOT)
                copy_output(ROOT, filename, loader, version)
            elif loader == 'forge':
                build_loader(loader, version, ROOT / 'forge')
                copy_output(ROOT / 'forge', filename, loader, version)
            elif loader == 'neoforge':
                build_loader(loader, version, ROOT / 'neoforge')
                copy_output(ROOT / 'neoforge', filename, loader, version)
            else:
                raise RuntimeError(f'Unsupported loader: {loader}')

    print('All release jars generated in', DIST_DIR)
    if args.upload:
        upload_to_github(args.release_tag, DIST_DIR)
        print('Uploaded jars to GitHub Release', args.release_tag)


if __name__ == '__main__':
    build_all(parse_args())
