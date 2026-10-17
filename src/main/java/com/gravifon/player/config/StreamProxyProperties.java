package com.gravifon.player.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gravifon.stream")
public record StreamProxyProperties(
        @NotNull @DurationMin(nanos = 1) Duration proxyConnectTimeout,
        @NotNull @DurationMin(nanos = 1) Duration proxyReadTimeout
) {}
