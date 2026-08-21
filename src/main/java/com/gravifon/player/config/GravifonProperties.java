package com.gravifon.player.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gravifon")
public class GravifonProperties {

    private Path musicRoot = Path.of("/music");
    private boolean startupScanEnabled = true;

    public Path getMusicRoot() {
        return musicRoot;
    }

    public void setMusicRoot(Path musicRoot) {
        this.musicRoot = musicRoot;
    }

    public boolean isStartupScanEnabled() {
        return startupScanEnabled;
    }

    public void setStartupScanEnabled(boolean startupScanEnabled) {
        this.startupScanEnabled = startupScanEnabled;
    }
}

