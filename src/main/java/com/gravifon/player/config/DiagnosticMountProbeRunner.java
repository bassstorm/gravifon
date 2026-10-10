package com.gravifon.player.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DiagnosticMountProbeRunner {
    private final LibraryProperties properties;
    private final Executor executor;

    public DiagnosticMountProbeRunner(
            LibraryProperties properties,
            @Qualifier("applicationTaskExecutor") Executor executor
    ) {
        this.properties = properties;
        this.executor = executor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Path root = properties.musicRoot().toAbsolutePath().normalize();
        executor.execute(() -> probe(root));
    }

    private void probe(Path root) {
        if (!Files.exists(root)) {
            log.info("Diagnostic mount probe: library path {} does not exist", root);
            return;
        }
        if (!Files.isDirectory(root)) {
            log.info("Diagnostic mount probe: library path {} is not a directory", root);
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            boolean empty = !stream.iterator().hasNext();
            if (empty) {
                log.info("Diagnostic mount probe: library path {} is empty", root);
            } else {
                log.info("Diagnostic mount probe: library path {} is present and populated", root);
            }
        } catch (IOException e) {
            log.info("Diagnostic mount probe: library path {} cannot be read: {}", root, e.getMessage());
        }
    }
}
