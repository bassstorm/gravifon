package com.gravifon.player.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GravifonPropertiesTest {

    @Test
    void defaultsAndMutatorsWork() {
        GravifonProperties properties = new GravifonProperties();

        assertThat(properties.getMusicRoot()).isEqualTo(Path.of("/music"));
        assertThat(properties.getConfigDir()).isEqualTo(Path.of("/config"));
        assertThat(properties.isStartupScanEnabled()).isTrue();

        properties.setMusicRoot(Path.of("/tmp/music"));
        properties.setConfigDir(Path.of("/tmp/config"));
        properties.setStartupScanEnabled(false);

        assertThat(properties.getMusicRoot()).isEqualTo(Path.of("/tmp/music"));
        assertThat(properties.getConfigDir()).isEqualTo(Path.of("/tmp/config"));
        assertThat(properties.isStartupScanEnabled()).isFalse();
    }
}