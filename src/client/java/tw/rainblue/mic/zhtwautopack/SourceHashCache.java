package tw.rainblue.mic.zhtwautopack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class SourceHashCache {
    private static final int FORMAT_VERSION = 1;
    private static final String CACHE_FILE = "ChineseBridge-source-cache.json";

    private final Path gameDirectory;
    private final Path cacheFile;
    private Snapshot stored;

    SourceHashCache(Path gameDirectory) {
        this.gameDirectory = gameDirectory;
        this.cacheFile = gameDirectory.resolve("config").resolve(CACHE_FILE);
        this.stored = load();
    }

    Snapshot capture() throws Exception {
        List<Path> files = trackedFiles();
        Map<String, FileHash> hashes = new LinkedHashMap<>();
        for (Path file : files) {
            String relative = normalize(gameDirectory.relativize(file));
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            FileHash previous = stored.files().get(relative);
            String sha256 = previous != null
                    && previous.size() == attributes.size()
                    && previous.modified() == attributes.lastModifiedTime().toMillis()
                    ? previous.sha256()
                    : hash(file);
            hashes.put(relative, new FileHash(
                    attributes.size(), attributes.lastModifiedTime().toMillis(), sha256));
        }

        MessageDigest aggregate = MessageDigest.getInstance("SHA-256");
        update(aggregate, "ChineseBridge-source-cache-v" + FORMAT_VERSION);
        hashes.forEach((path, file) -> {
            update(aggregate, path);
            update(aggregate, file.sha256());
        });
        return new Snapshot(HexFormat.of().formatHex(aggregate.digest()), hashes);
    }

    boolean contentChanged(Snapshot current) {
        return !current.aggregateHash().equals(stored.aggregateHash());
    }

    void save(Snapshot snapshot) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("formatVersion", FORMAT_VERSION);
        root.addProperty("aggregateSha256", snapshot.aggregateHash());
        JsonObject files = new JsonObject();
        snapshot.files().forEach((path, file) -> {
            JsonObject entry = new JsonObject();
            entry.addProperty("size", file.size());
            entry.addProperty("modified", file.modified());
            entry.addProperty("sha256", file.sha256());
            files.add(path, entry);
        });
        root.add("files", files);

        Files.createDirectories(cacheFile.getParent());
        Path temporary = cacheFile.resolveSibling(CACHE_FILE + ".tmp");
        try (Writer raw = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8);
             JsonWriter writer = new JsonWriter(raw)) {
            writer.setIndent("  ");
            com.google.gson.internal.Streams.write(root, writer);
        }
        Files.move(temporary, cacheFile, StandardCopyOption.REPLACE_EXISTING);
        stored = snapshot;
    }

    private Snapshot load() {
        if (!Files.exists(cacheFile)) return Snapshot.empty();
        try (Reader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.get("formatVersion").getAsInt() != FORMAT_VERSION) return Snapshot.empty();
            Map<String, FileHash> files = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("files").entrySet()) {
                JsonObject value = entry.getValue().getAsJsonObject();
                files.put(entry.getKey(), new FileHash(
                        value.get("size").getAsLong(),
                        value.get("modified").getAsLong(),
                        value.get("sha256").getAsString()));
            }
            return new Snapshot(root.get("aggregateSha256").getAsString(), files);
        } catch (Exception ignored) {
            return Snapshot.empty();
        }
    }

    private List<Path> trackedFiles() throws IOException {
        List<Path> files = new ArrayList<>();
        for (String directory : List.of("mods", "resourcepacks")) {
            Path root = gameDirectory.resolve(directory);
            if (!Files.exists(root)) continue;
            try (Stream<Path> paths = Files.walk(root, 8)) {
                paths.filter(this::isTracked).forEach(files::add);
            }
        }
        files.sort(Comparator.comparing(path -> normalize(gameDirectory.relativize(path))));
        return files;
    }

    private boolean isTracked(Path path) {
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString();
        if (name.endsWith(".tmp")) return false;
        if (name.startsWith("ChineseBridge-") && name.endsWith(".zip")) return false;
        return name.endsWith(".jar")
                || name.endsWith(".zip")
                || name.equals("zh_cn.json")
                || name.equals("zh_tw.json")
                || name.equals("zh_hk.json");
    }

    private static String hash(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        try (InputStream input = Files.newInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String normalize(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    record FileHash(long size, long modified, String sha256) { }

    record Snapshot(String aggregateHash, Map<String, FileHash> files) {
        static Snapshot empty() {
            return new Snapshot("", Map.of());
        }
    }
}
