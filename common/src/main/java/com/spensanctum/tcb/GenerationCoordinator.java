package com.spensanctum.tcb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

final class GenerationCoordinator {
    private static final long INITIAL_DELAY_MILLIS = 5_000;
    private static final long POLL_INTERVAL_MILLIS = 2_000;
    private static final long STABLE_DELAY_MILLIS = 3_000;
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private final Path gameDirectory;
    private final GeneratedPackGenerator generator;
    private final SourceHashCache sourceCache;
    private final Consumer<ReloadRequest> reloadResources;

    private GenerationCoordinator(Path gameDirectory, Consumer<ReloadRequest> reloadResources)
            throws IOException {
        this.gameDirectory = gameDirectory;
        this.generator = new GeneratedPackGenerator(gameDirectory);
        this.sourceCache = new SourceHashCache(gameDirectory);
        this.reloadResources = reloadResources;
    }

    static void start(Path gameDirectory, Consumer<ReloadRequest> reloadResources) {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread monitor = new Thread(() -> {
            try {
                new GenerationCoordinator(gameDirectory, reloadResources).monitor();
            } catch (Throwable exception) {
                System.err.println("[ChineseBridge] 無法啟動翻譯監控");
                exception.printStackTrace();
                STARTED.set(false);
            }
        }, "ChineseBridge translation monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    private void monitor() throws InterruptedException {
        Thread.sleep(INITIAL_DELAY_MILLIS);
        SourceHashCache.Snapshot initial = capture();
        String appliedHash = initial.aggregateHash();
        if (sourceCache.contentChanged(initial)
                || !Files.exists(generator.generatedPackPath())
                || generator.isInitialSetupPending()) {
            if (!rebuild(initial)) appliedHash = "";
        } else {
            System.out.println("[ChineseBridge] 模組與資源包內容未變，略過翻譯與資源重新載入。");
        }

        String observed = fingerprint();
        boolean pending = false;
        long stableSince = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(POLL_INTERVAL_MILLIS);
            String currentFingerprint = fingerprint();
            if (!currentFingerprint.equals(observed)) {
                observed = currentFingerprint;
                stableSince = System.currentTimeMillis();
                pending = true;
            }

            if (generator.isInitialSetupPending()) pending = true;
            if (pending && System.currentTimeMillis() - stableSince >= STABLE_DELAY_MILLIS) {
                SourceHashCache.Snapshot current = capture();
                boolean generatedMissing = !Files.exists(generator.generatedPackPath());
                if (!generatedMissing
                        && !generator.isInitialSetupPending()
                        && current.aggregateHash().equals(appliedHash)) {
                    saveCache(current);
                    observed = fingerprint();
                    pending = false;
                    continue;
                }
                if (rebuild(current)) {
                    appliedHash = current.aggregateHash();
                    observed = fingerprint();
                    pending = false;
                } else {
                    stableSince = System.currentTimeMillis();
                }
            }
        }
    }

    private SourceHashCache.Snapshot capture() {
        try {
            return sourceCache.capture();
        } catch (Throwable exception) {
            System.err.println("[ChineseBridge] 無法計算來源 hash，將重新掃描以確保翻譯正確");
            exception.printStackTrace();
            return new SourceHashCache.Snapshot(
                    "capture-error:" + System.nanoTime(), java.util.Map.of());
        }
    }

    private boolean rebuild(SourceHashCache.Snapshot snapshot) {
        try {
            GeneratedPackGenerator.Result result = generator.generate();
            saveCache(snapshot);
            System.out.printf("[ChineseBridge] 已更新 %s，掃描 %d 個來源、產生 %d 個語系檔。%n",
                    result.packFile(), result.sourcesRead(), result.filesWritten());
            if (result.packEnabled()) {
                reloadResources.accept(new ReloadRequest(
                        "file/" + result.packFile(), result.initialSetupPerformed()));
            }
            return true;
        } catch (Throwable exception) {
            System.err.println("[ChineseBridge] 無法更新中文語系資源包，稍後會重試");
            exception.printStackTrace();
            return false;
        }
    }

    private void saveCache(SourceHashCache.Snapshot snapshot) {
        try {
            sourceCache.save(snapshot);
        } catch (IOException exception) {
            System.err.println("[ChineseBridge] 無法儲存來源 hash 快取，下次啟動將重新掃描");
            exception.printStackTrace();
        }
    }

    private String fingerprint() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            List<Path> sources = new ArrayList<>();
            for (String directory : List.of("mods", "resourcepacks")) {
                Path root = gameDirectory.resolve(directory);
                if (!Files.exists(root)) continue;
                try (Stream<Path> paths = Files.walk(root, 8)) {
                    paths.filter(this::isTrackedSource).forEach(sources::add);
                }
            }
            sources.sort(Comparator.comparing(Path::toString));
            for (Path source : sources) {
                BasicFileAttributes attributes =
                        Files.readAttributes(source, BasicFileAttributes.class);
                update(digest, gameDirectory.relativize(source).toString());
                update(digest, Long.toString(attributes.size()));
                update(digest, Long.toString(attributes.lastModifiedTime().toMillis()));
            }
            update(digest, "generated=" + Files.exists(generator.generatedPackPath()));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception exception) {
            return "error:" + exception.getClass().getName() + ":" + exception.getMessage();
        }
    }

    private boolean isTrackedSource(Path path) {
        String name = path.getFileName().toString();
        if (name.equals(".ChineseBridge-staging") || name.endsWith(".tmp")) return false;
        if (name.startsWith("ChineseBridge-") && name.endsWith(".zip")) return false;
        return Files.isRegularFile(path)
                && (name.endsWith(".jar")
                || name.endsWith(".zip")
                || name.equals("zh_cn.json")
                || name.equals("zh_tw.json")
                || name.equals("zh_hk.json"));
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    record ReloadRequest(String packId, boolean selectAtHighestPriority) { }
}
