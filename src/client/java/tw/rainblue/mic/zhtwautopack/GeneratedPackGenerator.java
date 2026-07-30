package tw.rainblue.mic.zhtwautopack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;
import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reads language files directly from installed mod/resource-pack jars and folders.
 * A source namespace is omitted only when that same source already supplies zh_tw.json.
 */
final class GeneratedPackGenerator {
    static final String PACK_DIRECTORY = "ZH-TW Auto Pack";
    static final String PACK_FILE = "ChineseBridge-1.20.1.zip";
    private static final String CN_SUFFIX = "/lang/zh_cn.json";
    private static final String TW_SUFFIX = "/lang/zh_tw.json";
    private final Path gameDirectory;

    GeneratedPackGenerator(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    Path generatedPackPath() {
        return gameDirectory.resolve("resourcepacks").resolve(PACK_FILE);
    }

    Result generate() throws IOException {
        Map<String, JsonObject> simplified = new HashMap<>();
        int[] sources = {0};
        for (Path source : discoverSources()) {
            try {
                Map<String, JsonObject> sourceSimplified = new HashMap<>();
                Set<String> sourceTraditional = new HashSet<>();
                scan(source, sourceSimplified, sourceTraditional);
                sourceSimplified.forEach((namespace, translations) -> {
                    if (!sourceTraditional.contains(namespace)) {
                        JsonObject merged = simplified.computeIfAbsent(namespace, ignored -> new JsonObject());
                        translations.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                    }
                });
                sources[0]++;
            } catch (Exception ignored) {
                // A corrupt/unreadable third-party archive must not prevent the game from starting.
            }
        }

        Path pack = gameDirectory.resolve("resourcepacks").resolve(PACK_DIRECTORY);
        if (Files.exists(pack)) {
            try (Stream<Path> oldFiles = Files.walk(pack)) {
                for (Path old : oldFiles.sorted(Comparator.reverseOrder()).toList()) Files.delete(old);
            }
        }
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Auto-generated Traditional Chinese translations\"\n  }\n}\n", StandardCharsets.UTF_8);
        int written = 0;
        for (Map.Entry<String, JsonObject> entry : simplified.entrySet()) {
            Path target = pack.resolve("assets").resolve(entry.getKey()).resolve("lang/zh_tw.json");
            Files.createDirectories(target.getParent());
            writeTraditional(entry.getValue(), target);
            written++;
        }
        zipPack(pack);
        try (Stream<Path> staged = Files.walk(pack)) {
            for (Path path : staged.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
        return new Result(written, sources[0]);
    }

    private void zipPack(Path pack) throws IOException {
        Path zip = pack.getParent().resolve(PACK_FILE);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip)); Stream<Path> files = Files.walk(pack)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new ZipEntry(pack.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
    }

    private List<Path> discoverSources() throws IOException {
        List<Path> results = new ArrayList<>();
        for (String directory : List.of("mods", "resourcepacks")) {
            Path root = gameDirectory.resolve(directory);
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> directChildren = Files.newDirectoryStream(root)) {
                for (Path child : directChildren) {
                    String name = child.getFileName().toString();
                    if (name.equals(PACK_DIRECTORY) || name.equals(PACK_FILE)) continue;
                    if (Files.isDirectory(child)) {
                        results.add(child);
                    }
                }
            }
            try (Stream<Path> paths = Files.walk(root, 4)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return name.endsWith(".jar") || name.endsWith(".zip");
                        })
                        .filter(path -> !path.getFileName().toString().equals(PACK_FILE))
                        .forEach(results::add);
            }
        }
        return results;
    }

    private void scan(Path source, Map<String, JsonObject> simplified, Set<String> nativeTraditional) throws IOException {
        if (Files.isDirectory(source)) {
            scanRoot(source, simplified, nativeTraditional);
            return;
        }
        try (FileSystem zip = FileSystems.newFileSystem(source, (ClassLoader) null)) {
            scanRoot(zip.getPath("/"), simplified, nativeTraditional);
        }
    }

    private void scanRoot(Path root, Map<String, JsonObject> simplified, Set<String> nativeTraditional) throws IOException {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (Stream<Path> files = Files.walk(assets, 4)) {
            files.filter(Files::isRegularFile).forEach(path -> {
                String relative = assets.relativize(path).toString().replace('\\', '/');
                int separator = relative.indexOf('/');
                if (separator < 1) return;
                String namespace = relative.substring(0, separator);
                if (relative.endsWith(TW_SUFFIX)) nativeTraditional.add(namespace);
                if (relative.endsWith(CN_SUFFIX)) {
                    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        JsonElement parsed = JsonParser.parseReader(reader);
                        if (parsed.isJsonObject()) simplified.putIfAbsent(namespace, parsed.getAsJsonObject());
                    } catch (Exception ignored) { }
                }
            });
        }
    }

    private void writeTraditional(JsonObject source, Path target) throws IOException {
        JsonObject translated = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement value = entry.getValue();
            translated.add(entry.getKey(), value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? new JsonPrimitive(ZhConverterUtil.toTraditional(value.getAsString())) : value);
        }
        try (Writer raw = Files.newBufferedWriter(target, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             JsonWriter writer = new JsonWriter(raw)) {
            writer.setIndent("  ");
            com.google.gson.internal.Streams.write(translated, writer);
        }
    }

    record Result(int filesWritten, int sourcesRead) { }
}
