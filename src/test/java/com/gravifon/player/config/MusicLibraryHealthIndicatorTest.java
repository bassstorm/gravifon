package com.gravifon.player.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.actuate.health.Status;

class MusicLibraryHealthIndicatorTest {
    private static final Path MUSIC_ROOT = Path.of("/music");

    @Test
    void filesystemProbeRequiresAnExistingReadablePath(@TempDir Path tempDir) throws IOException {
        FileSystemMusicLibraryProbe probe = new FileSystemMusicLibraryProbe();
        Path missing = tempDir.resolve("missing");
        Files.createDirectory(tempDir.resolve("music"));

        assertThat(probe.isReadable(tempDir.resolve("music"))).isTrue();
        assertThat(probe.isReadable(missing)).isFalse();
    }

    @Test
    void reportsReadableAndUnavailablePaths() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            MusicLibraryHealthIndicator readable = indicator(path -> true, executor);
            MusicLibraryHealthIndicator missing = indicator(path -> false, executor);

            assertThat(readable.health().getStatus()).isEqualTo(Status.UP);
            assertThat(readable.health().getDetails()).containsEntry("readable", true);
            assertThat(missing.health().getStatus()).isEqualTo(Status.DOWN);
            assertThat(missing.health().getDetails()).containsEntry("readable", false);
        }
    }

    @Test
    void filesystemProbeRunsOnManagedExecutorRatherThanHealthRequestThread() throws Exception {
        try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
            AtomicReference<Thread> probeThread = new AtomicReference<>();
            MusicLibraryHealthIndicator indicator = indicator(path -> {
                probeThread.set(Thread.currentThread());
                return true;
            }, executor);
            Thread requestThread = Thread.currentThread();

            assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
            assertThat(probeThread.get()).isNotSameAs(requestThread);
            assertThat(probeThread.get().getName()).contains("pool-");
        }
    }

    @Test
    void timeoutReturnsDownWithoutWaitingForTheFilesystemCall() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch probeStarted = new CountDownLatch(1);
            CountDownLatch releaseProbe = new CountDownLatch(1);
            MusicLibraryHealthIndicator indicator = indicator(path -> {
                probeStarted.countDown();
                try {
                    releaseProbe.await();
                    return true;
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }, executor);

            long started = System.nanoTime();
            var health = indicator.health();
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            releaseProbe.countDown();

            assertThat(probeStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(health.getStatus()).isEqualTo(Status.DOWN);
            assertThat(health.getDetails()).containsEntry("reason", "probe timed out");
            assertThat(elapsedMillis).isLessThan(1_000);
        }
    }

    private MusicLibraryHealthIndicator indicator(MusicLibraryProbe probe, Executor executor) {
        return new MusicLibraryHealthIndicator(new LibraryProperties(MUSIC_ROOT, false), probe, executor);
    }
}
