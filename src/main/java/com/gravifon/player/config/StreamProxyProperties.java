package com.gravifon.player.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "gravifon.streams")
public record StreamProxyProperties(@NotNull Duration proxyConnectTimeout, @NotNull Duration proxyReadTimeout) {
    public StreamProxyProperties(
            @DefaultValue("PT5S") Duration proxyConnectTimeout,
            @DefaultValue("PT15S") Duration proxyReadTimeout
    ) {
        if (proxyConnectTimeout != null && (proxyConnectTimeout.isNegative() || proxyConnectTimeout.isZero())) {
            throw new IllegalArgumentException("proxyConnectTimeout must be positive");
        }
        if (proxyReadTimeout != null && (proxyReadTimeout.isNegative() || proxyReadTimeout.isZero())) {
            throw new IllegalArgumentException("proxyReadTimeout must be positive");
        }
        this.proxyConnectTimeout = proxyConnectTimeout;
        this.proxyReadTimeout = proxyReadTimeout;
    }
}
