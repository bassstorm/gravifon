package com.gravifon.player.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
class DiagnosticMountProbeRunnerTest {
    @Test
    void logsWhenPathDoesNotExist(CapturedOutput output, @TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        LibraryProperties properties = new LibraryProperties(missing, false);
        DiagnosticMountProbeRunner runner = new DiagnosticMountProbeRunner(properties, Runnable::run);

        runner.onApplicationReady();

        assertThat(output.getAll()).contains("does not exist");
    }

    @Test
    void logsWhenPathIsEmpty(CapturedOutput output, @TempDir Path tempDir) throws IOException {
        Path emptyDir = Files.createDirectory(tempDir.resolve("empty"));
        LibraryProperties properties = new LibraryProperties(emptyDir, false);
        DiagnosticMountProbeRunner runner = new DiagnosticMountProbeRunner(properties, Runnable::run);

        runner.onApplicationReady();

        assertThat(output.getAll()).contains("is empty");
    }

    @Test
    void logsWhenPathIsPopulated(CapturedOutput output, @TempDir Path tempDir) throws IOException {
        Path populated = Files.createDirectory(tempDir.resolve("populated"));
        Files.writeString(populated.resolve("song.mp3"), "audio");
        LibraryProperties properties = new LibraryProperties(populated, false);
        DiagnosticMountProbeRunner runner = new DiagnosticMountProbeRunner(properties, Runnable::run);

        runner.onApplicationReady();

        assertThat(output.getAll()).contains("is present and populated");
    }

    @Test
    void schedulesFilesystemProbeAfterApplicationIsReady(CapturedOutput output, @TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");
        AtomicReference<Runnable> scheduledProbe = new AtomicReference<>();
        DiagnosticMountProbeRunner runner =
                new DiagnosticMountProbeRunner(new LibraryProperties(missing, false), scheduledProbe::set);

        runner.onApplicationReady();

        assertThat(scheduledProbe.get()).isNotNull();
        assertThat(output.getAll()).doesNotContain("does not exist");

        scheduledProbe.get().run();

        assertThat(output.getAll()).contains("does not exist");
    }
}
