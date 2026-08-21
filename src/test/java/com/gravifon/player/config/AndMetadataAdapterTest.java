package com.gravifon.player.config;

import com.gravifon.player.catalog.metadata.JaudioTaggerTrackDurationExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class AndMetadataAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void properties_defaultAndMutatorsWork() {
        GravifonProperties properties = new GravifonProperties();

        assertThat(properties.getMusicRoot()).isEqualTo(Path.of("/music"));
        assertThat(properties.isStartupScanEnabled()).isTrue();

        properties.setMusicRoot(Path.of("/tmp/music"));
        properties.setStartupScanEnabled(false);

        assertThat(properties.getMusicRoot()).isEqualTo(Path.of("/tmp/music"));
        assertThat(properties.isStartupScanEnabled()).isFalse();
    }

    @Test
    void jaudioTaggerExtractor_returnsEmptyForUnreadableFile() throws IOException {
        Path invalidAudioFile = Files.createFile(tempDir.resolve("not-audio.mp3"));
        Files.writeString(invalidAudioFile, "not a valid audio stream");

        JaudioTaggerTrackDurationExtractor extractor = new JaudioTaggerTrackDurationExtractor();

        assertThat(extractor.extractDurationSeconds(invalidAudioFile)).isEmpty();
    }
}

