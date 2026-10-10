package com.gravifon.player.config;

import jakarta.validation.constraints.NotNull;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gravifon.library")
public record LibraryProperties(@NotNull Path musicRoot, boolean startupScanEnabled) {}
