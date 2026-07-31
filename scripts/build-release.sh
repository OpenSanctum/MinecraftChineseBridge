#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if command -v python3 >/dev/null 2>&1; then
	python3 "$SCRIPT_DIR/build-release.py" "$@"
elif command -v python >/dev/null 2>&1; then
	python "$SCRIPT_DIR/build-release.py" "$@"
else
	echo "[build-release] Python not found. Install Python 3." >&2
	exit 1
fi
