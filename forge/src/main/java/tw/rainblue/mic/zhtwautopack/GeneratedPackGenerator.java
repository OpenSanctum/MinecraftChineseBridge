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

/**
 * Reads language files directly from installed mod/resource-pack jars and folders.
 * A namespace is omitted whenever a native zh_tw.json is present in any scanned source.
 */
final class GeneratedPackGenerator {
    static final String PACK_DIRECTORY = "ZH-TW Auto Pack";
    private static final String CN_SUFFIX = "/lang/zh_cn.json";
    private static final String TW_SUFFIX = "/lang/zh_tw.json";
    private final Path gameDirectory;

    GeneratedPackGenerator(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
    }

    Result generate() throws IOException {
        Map<String, JsonObject> simplified = new HashMap<>();
        Set<String> nativeTraditional = new HashSet<>();
        int[] sources = {0};
        for (Path source : discoverSources()) {
            try {
                scan(source, simplified, nativeTraditional);
                sources[0]++;
            } catch (Exception ignored) {
                // A corrupt/unreadable third-party archive must not prevent the game from starting.
            }
        }

        Path pack = gameDirectory.resolve("resourcepacks").resolve(PACK_DIRECTORY);
        Files.createDirectories(pack);
        Files.writeString(pack.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"pack_format\": 15,\n    \"description\": \"Auto-generated Traditional Chinese translations\"\n  }\n}\n", StandardCharsets.UTF_8);
        int written = 0;
        for (Map.Entry<String, JsonObject> entry : simplified.entrySet()) {
            if (nativeTraditional.contains(entry.getKey())) continue;
            Path target = pack.resolve("assets").resolve(entry.getKey()).resolve("lang/zh_tw.json");
            Files.createDirectories(target.getParent());
            writeTraditional(entry.getValue(), target);
            written++;
        }
        return new Result(written, sources[0]);
    }

    private List<Path> discoverSources() throws IOException {
        List<Path> results = new ArrayList<>();
        for (String directory : List.of("mods", "resourcepacks")) {
            Path root = gameDirectory.resolve(directory);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> paths = Files.walk(root, 3)) {
                paths.filter(path -> Files.isDirectory(path) || path.toString().endsWith(".jar") || path.toString().endsWith(".zip"))
                        .filter(path -> !path.getFileName().toString().equals(PACK_DIRECTORY))
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
