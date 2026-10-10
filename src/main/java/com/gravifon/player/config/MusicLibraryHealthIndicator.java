package com.gravifon.player.config;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Slf4j
@Component("musicLibrary")
public class MusicLibraryHealthIndicator implements HealthIndicator {
    private static final long PROBE_TIMEOUT_MILLIS = 500;
    private final Path musicRoot;
    private final MusicLibraryProbe probe;
    private final Executor executor;

    public MusicLibraryHealthIndicator(
            LibraryProperties properties,
            MusicLibraryProbe probe,
            @Qualifier("applicationTaskExecutor") Executor executor
    ) {
        this.musicRoot = properties.musicRoot();
        this.probe = probe;
        this.executor = executor;
    }

    @Override
    public Health health() {
        FutureTask<Boolean> probeTask = new FutureTask<>(() -> probe.isReadable(musicRoot));
        try {
            executor.execute(probeTask);
            if (probeTask.get(PROBE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                return Health.up().withDetail("path", musicRoot).withDetail("readable", true).build();
            }
            return Health.down().withDetail("path", musicRoot).withDetail("readable", false).build();
        } catch (TimeoutException timeout) {
            probeTask.cancel(true);
            log.warn("Music library health probe timed out: path={}", musicRoot);
            return Health.down().withDetail("path", musicRoot).withDetail("reason", "probe timed out").build();
        } catch (InterruptedException interrupted) {
            probeTask.cancel(true);
            Thread.currentThread().interrupt();
            return Health.down().withDetail("path", musicRoot).withDetail("reason", "probe interrupted").build();
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            log.warn("Music library health probe failed: path={}", musicRoot, cause);
            return Health
                .down()
                .withDetail("path", musicRoot)
                .withDetail("reason", cause == null ? "probe failed" : cause.toString())
                .build();
        } catch (RuntimeException failure) {
            log.warn("Music library health probe could not be scheduled: path={}", musicRoot, failure);
            return Health
                .down()
                .withDetail("path", musicRoot)
                .withDetail("reason", "probe could not be scheduled")
                .build();
        }
    }
}
