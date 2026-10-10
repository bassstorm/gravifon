package com.gravifon.player.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gravifon.streams")
public record StreamRefreshProperties(
        @NotNull Duration refreshAhead,
        @Min(1) int refreshUpcomingCount,
        @NotNull Duration refreshMinValidity,
        @NotNull Duration refreshTimeout,
        @Min(1) int refreshMaxAttempts
) {
    public StreamRefreshProperties(
            @DefaultValue("PT1H") Duration refreshAhead,
            @DefaultValue("10") int refreshUpcomingCount,
            @DefaultValue("PT1M") Duration refreshMinValidity,
            @DefaultValue("PT5S") Duration refreshTimeout,
            @DefaultValue("2") int refreshMaxAttempts
    ) {
        if (refreshAhead != null && (refreshAhead.isNegative() || refreshAhead.isZero())) {
            throw new IllegalArgumentException("refreshAhead must be positive");
        }
        if (refreshTimeout != null && (refreshTimeout.isNegative() || refreshTimeout.isZero())) {
            throw new IllegalArgumentException("refreshTimeout must be positive");
        }
        if (refreshMinValidity != null && (refreshMinValidity.isNegative() || refreshMinValidity.isZero())) {
            throw new IllegalArgumentException("refreshMinValidity must be positive");
        }
        if (refreshUpcomingCount < 1) {
            throw new IllegalArgumentException("refreshUpcomingCount must be at least 1");
        }
        if (refreshMaxAttempts < 1) {
            throw new IllegalArgumentException("refreshMaxAttempts must be at least 1");
        }
        this.refreshAhead = refreshAhead;
        this.refreshUpcomingCount = refreshUpcomingCount;
        this.refreshMinValidity = refreshMinValidity;
        this.refreshTimeout = refreshTimeout;
        this.refreshMaxAttempts = refreshMaxAttempts;
    }
}
