package com.gravifon.player.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gravifon.sessions")
public record SessionProperties(
        @NotNull @DurationMin(nanos = 1) Duration inactivityTimeout,
        @NotBlank String cleanupCron
) {}
