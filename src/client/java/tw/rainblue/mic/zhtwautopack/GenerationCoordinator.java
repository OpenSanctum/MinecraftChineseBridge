package tw.rainblue.mic.zhtwautopack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

final class GenerationCoordinator {
    private static final long INITIAL_DELAY_MILLIS = 4_000;
    private static final AtomicBoolean STARTED = new AtomicBoolean();

    private final GeneratedPackGenerator generator;
    private final SourceHashCache cache;
    private final Consumer<UpdateResult> onUpdated;
    private final Consumer<Throwable> onFailed;

    private GenerationCoordinator(Path gameDirectory, Consumer<UpdateResult> onUpdated,
                                  Consumer<Throwable> onFailed) throws Exception {
        this.generator = new GeneratedPackGenerator(gameDirectory);
        this.cache = new SourceHashCache(gameDirectory);
        this.onUpdated = onUpdated;
        this.onFailed = onFailed;
    }

    static void start(Path gameDirectory, Consumer<UpdateResult> onUpdated,
                      Consumer<Throwable> onFailed) {
        if (!STARTED.compareAndSet(false, true)) return;
        Thread worker = new Thread(() -> {
            try {
                new GenerationCoordinator(gameDirectory, onUpdated, onFailed).run();
            } catch (Throwable exception) {
                onFailed.accept(exception);
                STARTED.set(false);
            }
        }, "ChineseBridge generation coordinator");
        worker.setDaemon(true);
        worker.start();
    }

    private void run() throws Exception {
        Thread.sleep(INITIAL_DELAY_MILLIS);
        SourceHashCache.Snapshot current = cache.capture();
        boolean changed = cache.contentChanged(current);
        boolean missingPack = !Files.exists(generator.generatedPackPath());
        if (!changed && !missingPack) return;

        GeneratedPackGenerator.Result result = generator.generate();
        cache.save(current);
        onUpdated.accept(new UpdateResult(
                "file/" + GeneratedPackGenerator.PACK_FILE,
                result.filesWritten(),
                result.sourcesRead()));
    }

    record UpdateResult(String packId, int filesWritten, int sourcesRead) { }
}
