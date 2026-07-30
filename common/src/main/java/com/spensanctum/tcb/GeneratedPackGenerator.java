package com.spensanctum.tcb;

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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class GeneratedPackGenerator {
    private static final Pattern RELEASE_JAR =
        Pattern.compile("^ChineseBridge-(?:fabric|forge|neoforge)-(.+)-(?:\\d{3})\\.jar$");
    private static final String OLD_PACK_ID = "file/ZH-TW Auto Pack";
    private static final String STATE_FILE = "ChineseBridge-state.json";
    private final Path gameDirectory;
    private final String minecraftVersion;
    private final String packFile;
    private final TaiwaneseLocalizer localizer;

    GeneratedPackGenerator(Path gameDirectory) throws IOException {
        this.gameDirectory = gameDirectory;
        this.minecraftVersion = detectMinecraftVersion();
        this.packFile = "ChineseBridge-" + minecraftVersion + ".zip";
        this.localizer = new TaiwaneseLocalizer(gameDirectory);
    }

    Result generate() throws IOException {
        Map<String, EnumMap<ChineseLocale, JsonObject>> translations = new HashMap<>();
        int sources = 0;
        for (Path source : discoverSources()) {
            try {
                scan(source, translations);
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
        for (Map.Entry<String, EnumMap<ChineseLocale, JsonObject>> entry : translations.entrySet()) {
            EnumMap<ChineseLocale, JsonObject> available = entry.getValue();
            for (ChineseLocale targetLocale : ChineseLocale.values()) {
                if (available.containsKey(targetLocale)) continue;
                JsonObject source = sourceFor(targetLocale, available);
                if (source == null) continue;
                Path target = staging.resolve("assets").resolve(entry.getKey())
                        .resolve("lang").resolve(targetLocale.fileName);
                Files.createDirectories(target.getParent());
                writeLocalized(source, target, targetLocale);
                written++;
            }
        }

        Path temporaryZip = resourcePacks.resolve(packFile + ".tmp");
        Path finalZip = resourcePacks.resolve(packFile);
        Files.deleteIfExists(temporaryZip);
        zip(staging, temporaryZip);
        Files.move(temporaryZip, finalZip, StandardCopyOption.REPLACE_EXISTING);
        deleteTree(staging);
        boolean initialSetupPerformed = enableOnFirstRun();
        return new Result(written, sources, packFile, initialSetupPerformed, isPackEnabled());
    }

    private List<Path> discoverSources() throws IOException {
        List<Path> results = new ArrayList<>();
        for (String directory : List.of("mods", "resourcepacks")) {
            Path root = gameDirectory.resolve(directory);
            if (!Files.isDirectory(root)) continue;
            try (Stream<Path> paths = Files.walk(root, 3)) {
                paths.filter(path -> Files.isDirectory(path)
                                || path.toString().endsWith(".jar")
                                || path.toString().endsWith(".zip"))
                        .filter(path -> {
                            String name = path.getFileName().toString();
                            return !name.equals(".ChineseBridge-staging")
                                    && !(name.startsWith("ChineseBridge-") && name.endsWith(".zip"));
                        })
                        .forEach(results::add);
            }
        }
        return results;
    }

    private void scan(Path source,
                      Map<String, EnumMap<ChineseLocale, JsonObject>> translations) throws IOException {
        if (Files.isDirectory(source)) {
            scanRoot(source, translations);
            return;
        }
        try (FileSystem zip = FileSystems.newFileSystem(source, (ClassLoader) null)) {
            scanRoot(zip.getPath("/"), translations);
        }
    }

    private void scanRoot(Path root,
                          Map<String, EnumMap<ChineseLocale, JsonObject>> translations)
            throws IOException {
        Path assets = root.resolve("assets");
        if (!Files.isDirectory(assets)) return;
        try (Stream<Path> files = Files.walk(assets, 4)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                String relative = assets.relativize(path).toString().replace('\\', '/');
                int separator = relative.indexOf('/');
                if (separator < 1) continue;
                String namespace = relative.substring(0, separator);
                ChineseLocale locale = ChineseLocale.fromPath(relative);
                if (locale != null) {
                    try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                        JsonElement parsed = JsonParser.parseReader(reader);
                        if (parsed.isJsonObject()) {
                            JsonObject merged = translations
                                    .computeIfAbsent(namespace,
                                            ignored -> new EnumMap<>(ChineseLocale.class))
                                    .computeIfAbsent(locale, ignored -> new JsonObject());
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

    private boolean enableOnFirstRun() throws IOException {
        Path state = gameDirectory.resolve("config").resolve(STATE_FILE);
        if (Files.exists(state)) return false;

        Path options = gameDirectory.resolve("options.txt");
        if (!Files.exists(options)) return false;
        List<String> lines = Files.readAllLines(options, StandardCharsets.UTF_8);
        String wanted = "file/" + packFile;
        boolean found = false;
        boolean alreadyEnabled = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (!line.startsWith("resourcePacks:")) continue;
            JsonArray packs;
            try {
                packs = JsonParser.parseString(line.substring("resourcePacks:".length())).getAsJsonArray();
            } catch (Exception ignored) {
                packs = new JsonArray();
            }
            for (JsonElement element : packs) {
                if (wanted.equals(element.getAsString())) {
                    alreadyEnabled = true;
                    break;
                }
            }
            if (alreadyEnabled) {
                found = true;
                break;
            }
            JsonArray updated = new JsonArray();
            for (JsonElement element : packs) {
                String id = element.getAsString();
                if (!id.equals(OLD_PACK_ID)
                        && !id.startsWith("file/ChineseBridge-")) updated.add(id);
            }
            updated.add(wanted);
            lines.set(index, "resourcePacks:" + updated);
            found = true;
            break;
        }
        if (!alreadyEnabled) {
            if (!found) lines.add("resourcePacks:[\"" + wanted + "\"]");
            Files.write(options, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
        Files.createDirectories(state.getParent());
        Path temporaryState = state.resolveSibling(STATE_FILE + ".tmp");
        Files.writeString(temporaryState, """
                {
                  "initialResourcePackSetupCompleted": true
                }
                """, StandardCharsets.UTF_8);
        Files.move(temporaryState, state, StandardCopyOption.REPLACE_EXISTING);
        return !alreadyEnabled;
    }

    boolean isInitialSetupPending() {
        return !Files.exists(gameDirectory.resolve("config").resolve(STATE_FILE))
                && Files.exists(gameDirectory.resolve("options.txt"));
    }

    Path generatedPackPath() {
        return gameDirectory.resolve("resourcepacks").resolve(packFile);
    }

    private boolean isPackEnabled() {
        Path options = gameDirectory.resolve("options.txt");
        if (!Files.exists(options)) return false;
        String wanted = "file/" + packFile;
        try {
            for (String line : Files.readAllLines(options, StandardCharsets.UTF_8)) {
                if (!line.startsWith("resourcePacks:")) continue;
                JsonArray packs = JsonParser.parseString(
                        line.substring("resourcePacks:".length())).getAsJsonArray();
                for (JsonElement element : packs) {
                    if (wanted.equals(element.getAsString())) return true;
                }
            }
        } catch (Exception ignored) {
            // A malformed options file must not prevent pack generation.
        }
        return false;
    }

    private JsonObject sourceFor(ChineseLocale target,
                                 EnumMap<ChineseLocale, JsonObject> available) {
        ChineseLocale[] preference = switch (target) {
            case CN -> new ChineseLocale[]{ChineseLocale.TW, ChineseLocale.HK};
            case TW -> new ChineseLocale[]{ChineseLocale.CN, ChineseLocale.HK};
            case HK -> new ChineseLocale[]{ChineseLocale.TW, ChineseLocale.CN};
        };
        for (ChineseLocale locale : preference) {
            JsonObject source = available.get(locale);
            if (source != null) return source;
        }
        return null;
    }

    private void writeLocalized(JsonObject source, Path target, ChineseLocale locale)
            throws IOException {
        JsonObject translated = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
            JsonElement value = entry.getValue();
            translated.add(entry.getKey(), value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                    ? new JsonPrimitive(localize(value.getAsString(), locale)) : value);
        }
        try (Writer raw = Files.newBufferedWriter(target, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(raw)) {
            writer.setIndent("  ");
            com.google.gson.internal.Streams.write(translated, writer);
        }
    }

    private String localize(String source, ChineseLocale locale) {
        return switch (locale) {
            case CN -> localizer.toSimplified(source);
            case TW -> localizer.localize(source);
            case HK -> localizer.toHongKong(source);
        };
    }

    private void writePackIcon(Path target) throws IOException {
        try (InputStream icon = GeneratedPackGenerator.class.getResourceAsStream("/ChineseBridge-pack.png")) {
            if (icon == null) throw new IOException("找不到內建資源包圖示 ChineseBridge-pack.png");
            Files.copy(icon, target, StandardCopyOption.REPLACE_EXISTING);
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

    private String detectMinecraftVersion() {
        String override = System.getProperty("chinesebridge.minecraftVersion");
        if (override != null && !override.isBlank()) return override;
        try {
            CodeSource source = GeneratedPackGenerator.class.getProtectionDomain().getCodeSource();
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
                                        "description": "ChineseBridge 自動補充的中文語系"
                  }
                }
                """;
    }

    record Result(int filesWritten, int sourcesRead, String packFile,
                  boolean initialSetupPerformed, boolean packEnabled) { }

    private enum ChineseLocale {
        CN("zh_cn.json"),
        TW("zh_tw.json"),
        HK("zh_hk.json");

        private final String fileName;

        ChineseLocale(String fileName) {
            this.fileName = fileName;
        }

        private static ChineseLocale fromPath(String path) {
            for (ChineseLocale locale : values()) {
                if (path.endsWith("/lang/" + locale.fileName)) return locale;
            }
            return null;
        }
    }
}
