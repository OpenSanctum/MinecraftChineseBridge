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
    private final Consumer<ReloadRequest> reloadResources;

    private GenerationCoordinator(Path gameDirectory, Consumer<ReloadRequest> reloadResources)
            throws IOException {
        this.gameDirectory = gameDirectory;
        this.generator = new GeneratedPackGenerator(gameDirectory);
        this.reloadResources = reloadResources;
    }

    static void start(Path gameDirectory, Consumer<ReloadRequest> reloadResources) {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread monitor = new Thread(() -> {
            try {
                new GenerationCoordinator(gameDirectory, reloadResources).monitor();
            } catch (Exception exception) {
                System.err.println("[TChineseB] 無法啟動翻譯監控");
                exception.printStackTrace();
                STARTED.set(false);
            }
        }, "TChineseB translation monitor");
        monitor.setDaemon(true);
        monitor.start();
    }

    private void monitor() throws InterruptedException {
        Thread.sleep(INITIAL_DELAY_MILLIS);
        rebuild();

        String observed = fingerprint();
        boolean pending = false;
        long stableSince = System.currentTimeMillis();
        while (!Thread.currentThread().isInterrupted()) {
            Thread.sleep(POLL_INTERVAL_MILLIS);
            String current = fingerprint();
            if (!current.equals(observed)) {
                observed = current;
                stableSince = System.currentTimeMillis();
                pending = true;
            }

            if (generator.isInitialSetupPending()) pending = true;
            if (pending && System.currentTimeMillis() - stableSince >= STABLE_DELAY_MILLIS) {
                if (rebuild()) {
                    observed = fingerprint();
                    pending = false;
                } else {
                    stableSince = System.currentTimeMillis();
                }
            }
        }
    }

    private boolean rebuild() {
        try {
            GeneratedPackGenerator.Result result = generator.generate();
            System.out.printf("[TChineseB] 已更新 %s，掃描 %d 個來源、產生 %d 個語系檔。%n",
                    result.packFile(), result.sourcesRead(), result.filesWritten());
            if (result.packEnabled()) {
                reloadResources.accept(new ReloadRequest(
                        "file/" + result.packFile(), result.initialSetupPerformed()));
            }
            return true;
        } catch (Exception exception) {
            System.err.println("[TChineseB] 無法更新繁體中文資源包，稍後會重試");
            exception.printStackTrace();
            return false;
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
        if (name.equals(".tchineseb-staging") || name.endsWith(".tmp")) return false;
        if (name.startsWith("TChineseB-") && name.endsWith(".zip")) return false;
        return Files.isRegularFile(path)
                && (name.endsWith(".jar")
                || name.endsWith(".zip")
                || name.equals("zh_cn.json")
                || name.equals("zh_tw.json"));
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    record ReloadRequest(String packId, boolean selectAtHighestPriority) { }
}
