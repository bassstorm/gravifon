package com.gravifon.player.config;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gravifon.library")
public record LibraryProperties(@NotNull Path musicRoot, boolean startupScanEnabled) {
    public LibraryProperties(@DefaultValue("/music") Path musicRoot, @DefaultValue("true") boolean startupScanEnabled) {
        this.musicRoot = musicRoot;
        this.startupScanEnabled = startupScanEnabled;
    }
}
