package tw.rainblue.mic.zhtwautopack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class SharedGeneratedPackGenerator {
    private static final Pattern RELEASE_JAR =
            Pattern.compile("^ChineseBridge-(?:fabric|forge|neoforge)-(.+)-(?:\\d{3})\\.jar$");
    private static final String CN_SUFFIX = "/lang/zh_cn.json";
    private static final String TW_SUFFIX = "/lang/zh_tw.json";
    private static final String OLD_PACK_ID = "file/ZH-TW Auto Pack";
    private final Path gameDirectory;
    private final String minecraftVersion;
    private final String packFile;
    private final TaiwaneseLocalizer localizer;
    private final SourceHashCache sourceHashCache;

    SharedGeneratedPackGenerator(Path gameDirectory) throws IOException {
        this.gameDirectory = gameDirectory;
        this.minecraftVersion = detectMinecraftVersion();
        this.packFile = "ChineseBridge-" + minecraftVersion + ".zip";
        this.localizer = new TaiwaneseLocalizer(gameDirectory);
        this.sourceHashCache = new SourceHashCache(gameDirectory);
    }

    Result generateAndEnable() throws IOException {
        SourceHashCache.Snapshot snapshot = null;
        try {
            snapshot = sourceHashCache.capture();
            if (!sourceHashCache.contentChanged(snapshot) && Files.exists(generatedPackPath())) {
                return new Result(0, 0, packFile);
            }
        } catch (Exception ignored) {
            // If cache read/capture fails, continue with full generation to stay correct.
        }

        Map<String, JsonObject> simplified = new HashMap<>();
        int sources = 0;
        for (Path source : discoverSources()) {
            try {
                Map<String, JsonObject> local = new HashMap<>();
                Set<String> localTraditional = new HashSet<>();
                scan(source, local, localTraditional);
                local.forEach((namespace, translations) -> {
                    if (!localTraditional.contains(namespace)) {
                        JsonObject merged = simplified.computeIfAbsent(namespace, ignored -> new JsonObject());
                        translations.entrySet().forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                    }
                });
                sources++;
            } catch (Exception ignored) {
                // One broken third-party archive must not prevent Minecraft from starting.
            }
        }

        Path resourcePacks = gameDirectory.resolve("resourcepacks");
        Files.createDirectories(resourcePacks);
        Path staging = resourcePacks.resolve(".ChineseBridge-staging");
        deleteTree(staging);
        Files.createDirectories(staging);
        Files.writeString(staging.resolve("pack.mcmeta"), packMetadata(), StandardCharsets.UTF_8);
        writePackIcon(staging.resolve("pack.png"));

        int written = 0;
        for (Map.Entry<String, JsonObject> entry : simplified.entrySet()) {
            Path target = staging.resolve("assets").resolve(entry.getKey()).resolve("lang/zh_tw.json");
            Files.createDirectories(target.getParent());
            writeTraditional(entry.getValue(), target);
            written++;
        }

        Path temporaryZip = resourcePacks.resolve(packFile + ".tmp");
        Path finalZip = resourcePacks.resolve(packFile);
        Files.deleteIfExists(temporaryZip);
        zip(staging, temporaryZip);
        Files.move(temporaryZip, finalZip, StandardCopyOption.REPLACE_EXISTING);
        deleteTree(staging);
        enableInOptions();
        if (snapshot != null) {
            try {
                sourceHashCache.save(snapshot);
            } catch (Exception ignored) {
                // Cache save failure should not fail startup.
            }
        }
        return new Result(written, sources, packFile);
    }

    private Path generatedPackPath() {
        return gameDirectory.resolve("resourcepacks").resolve(packFile);
    }

    private List<Path> discoverSources() throws IOException {
        List<Path> results = new ArrayList<>();
        for (String directory : List.of("mods", "resourcepacks")) {
            Path root = gameDirectory.resolve(directory);
            if (!Files.isDirectory(root)) continue;
            try (DirectoryStream<Path> directChildren = Files.newDirectoryStream(root)) {
                for (Path child : directChildren) {
                    if (!Files.isDirectory(child)) continue;
                    String name = child.getFileName().toString();
                    if (name.equals(".ChineseBridge-staging")) continue;
                    results.add(child);
                }
            }
            try (Stream<Path> paths = Files.walk(root, 4)) {
                paths.filter(Files::isRegularFile)
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return (name.endsWith(".jar") || name.endsWith(".zip"))
                                    && !(name.startsWith("ChineseBridge-") && name.endsWith(".zip"));
                        })
                        .forEach(results::add);
            }
        }
        return results;
    }

    private void scan(Path source, Map<String, JsonObject> simplified,
                      Set<String> nativeTraditional) throws IOException {
        if (Files.isDirectory(source)) {
            scanRoot(source, simplified, nativeTraditional);
            return;
        }
        try (FileSystem zip = FileSystems.newFileSystem(source, (ClassLoader) null)) {
            scanRoot(zip.getPath("/"), simplified, nativeTraditional);
        }
    }

    private void scanRoot(Path root, Map<String, JsonObject> simplified,
                          Set<String> nativeTraditional) throws IOException {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (Stream<Path> files = Files.walk(assets, 4)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String relative = assets.relativize(path).toString().replace('\\', '/');
                int separator = relative.indexOf('/');
                if (separator < 1) continue;
                String namespace = relative.substring(0, separator);
                if (relative.endsWith(TW_SUFFIX)) nativeTraditional.add(namespace);
                if (relative.endsWith(CN_SUFFIX)) {
                    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        JsonElement parsed = JsonParser.parseReader(reader);
                        if (parsed.isJsonObject()) {
                            JsonObject merged = simplified.computeIfAbsent(namespace, ignored -> new JsonObject());
                            parsed.getAsJsonObject().entrySet()
                                    .forEach(entry -> merged.add(entry.getKey(), entry.getValue()));
                        }
                    } catch (Exception ignored) {
                        // Ignore only the invalid language file.
                    }
                }
            }
        }
    }

    private void enableInOptions() throws IOException {
        Path options = gameDirectory.resolve("options.txt");
        if (!Files.exists(options)) return;
        List<String> lines = Files.readAllLines(options, StandardCharsets.UTF_8);
        String wanted = "file/" + packFile;
        boolean found = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.startsWith("resourcePacks:")) continue;
            JsonArray packs;
            try {
                packs = JsonParser.parseString(line.substring("resourcePacks:".length())).getAsJsonArray();
            } catch (Exception ignored) {
                packs = new JsonArray();
            }
            JsonArray updated = new JsonArray();
            for (JsonElement element : packs) {
                String id = element.getAsString();
                if (!id.equals(OLD_PACK_ID) && !id.startsWith("file/ChineseBridge-")) updated.add(id);
            }
            updated.add(wanted);
            lines.set(index, "resourcePacks:" + updated);
            found = true;
            break;
        }
        if (!found) lines.add("resourcePacks:[\"" + wanted + "\"]");
        Files.write(options, lines, StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private void writeTraditional(JsonObject source, Path target) throws IOException {
        JsonObject translated = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement value = entry.getValue();
            translated.add(entry.getKey(), value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? new JsonPrimitive(localizer.localize(value.getAsString())) : value);
        }
        try (Writer raw = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(raw)) {
            writer.setIndent("  ");
            com.google.gson.internal.Streams.write(translated, writer);
        }
    }

    private void zip(Path root, Path target) throws IOException {
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(target));
             Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private void writePackIcon(Path target) throws IOException {
        try (InputStream icon = SharedGeneratedPackGenerator.class.getResourceAsStream("/ChineseBridge-pack.png")) {
            if (icon == null) throw new IOException("找不到內建資源包圖示 ChineseBridge-pack.png");
            Files.copy(icon, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String detectMinecraftVersion() {
        String override = System.getProperty("chinesebridge.minecraftVersion");
        if (override != null && !override.isBlank()) return override;
        try {
            CodeSource source = SharedGeneratedPackGenerator.class.getProtectionDomain().getCodeSource();
            if (source != null) {
                String name = Path.of(source.getLocation().toURI()).getFileName().toString();
                Matcher matcher = RELEASE_JAR.matcher(name);
                if (matcher.matches()) return matcher.group(1);
            }
        } catch (Exception ignored) {
        }
        return "1.20.1";
    }

    private String packMetadata() {
        return """
                {
                  "pack": {
                    "pack_format": 15,
                    "supported_formats": {
                      "min_inclusive": 15,
                      "max_inclusive": 999
                    },
                                                                                "description": "中文橋接模組資源包"
                  }
                }
                """;
    }

    record Result(int filesWritten, int sourcesRead, String packFile) { }
}
