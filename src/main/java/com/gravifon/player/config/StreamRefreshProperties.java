package com.gravifon.player.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gravifon.streams")
public record StreamRefreshProperties(
        @NotNull @DurationMin(nanos = 1) Duration refreshAhead,
        @Min(1) int refreshUpcomingCount,
        @NotNull @DurationMin(nanos = 1) Duration refreshMinValidity,
        @NotNull @DurationMin(nanos = 1) Duration refreshTimeout,
        @Min(1) int refreshMaxAttempts
) {}
