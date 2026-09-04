package com.gravifon.player.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gravifon")
public class GravifonProperties {
    private Path musicRoot = Path.of("/music");
    private Path configDir = Path.of("/config");
    private boolean startupScanEnabled = true;
    private final Streams streams = new Streams();

    public Streams getStreams() {
        return streams;
    }

    public static class Streams {
        private boolean refreshEnabled = true;
        private Duration refreshAhead = Duration.ofHours(1);
        private Duration refreshInterval = Duration.ofMinutes(10);
        private String refreshScope = "ACTIVE_PLAYLIST_PLUS_NEXT";
        private Duration refreshTimeout = Duration.ofSeconds(5);
        private int refreshMaxAttempts = 2;

        public boolean isRefreshEnabled() {
            return refreshEnabled;
        }

        public void setRefreshEnabled(boolean refreshEnabled) {
            this.refreshEnabled = refreshEnabled;
        }

        public Duration getRefreshAhead() {
            return refreshAhead;
        }

        public void setRefreshAhead(Duration refreshAhead) {
            this.refreshAhead = refreshAhead;
        }

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public String getRefreshScope() {
            return refreshScope;
        }

        public void setRefreshScope(String refreshScope) {
            this.refreshScope = refreshScope;
        }

        public Duration getRefreshTimeout() {
            return refreshTimeout;
        }

        public void setRefreshTimeout(Duration refreshTimeout) {
            this.refreshTimeout = refreshTimeout;
        }

        public int getRefreshMaxAttempts() {
            return refreshMaxAttempts;
        }

        public void setRefreshMaxAttempts(int refreshMaxAttempts) {
            this.refreshMaxAttempts = refreshMaxAttempts;
        }
    }

    public Path getConfigDir() {
        return configDir;
    }

    public void setConfigDir(Path configDir) {
        this.configDir = configDir;
    }

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
