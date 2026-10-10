package com.gravifon.player.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class ScopedPropertiesTest {
    @Test
    void libraryPropertiesDefaults() {
        LibraryProperties properties = new LibraryProperties(Path.of("/music"), true);
        assertThat(properties.musicRoot()).isEqualTo(Path.of("/music"));
        assertThat(properties.startupScanEnabled()).isTrue();
    }

    @Test
    void streamRefreshPropertiesDefaults() {
        StreamRefreshProperties properties =
                new StreamRefreshProperties(Duration.ofHours(1), 10, Duration.ofMinutes(1), Duration.ofSeconds(5), 2);
        assertThat(properties.refreshAhead()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.refreshUpcomingCount()).isEqualTo(10);
        assertThat(properties.refreshMinValidity()).isEqualTo(Duration.ofMinutes(1));
        assertThat(properties.refreshTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.refreshMaxAttempts()).isEqualTo(2);
    }

    @Test
    void streamRefreshPropertiesRejectsNegativeOrZeroDurations() {
        assertThatThrownBy(() -> new StreamRefreshProperties(
                Duration.ofSeconds(-1),
                10,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                2
        ))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new StreamRefreshProperties(
                Duration.ofHours(1),
                10,
                Duration.ofMinutes(1),
                Duration.ofSeconds(-5),
                2
        ))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void streamRefreshPropertiesRejectsInvalidRefreshPolicy() {
        assertThatThrownBy(() -> new StreamRefreshProperties(
                Duration.ofHours(1),
                0,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                2
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("refreshUpcomingCount");

        assertThatThrownBy(() -> new StreamRefreshProperties(
                Duration.ofHours(1),
                10,
                Duration.ZERO,
                Duration.ofSeconds(5),
                2
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("refreshMinValidity");

        assertThatThrownBy(() -> new StreamRefreshProperties(
                Duration.ofHours(1),
                10,
                Duration.ofMinutes(1),
                Duration.ofSeconds(5),
                0
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("refreshMaxAttempts");
    }

    @Test
    void streamRefreshPropertiesAllowsIndependentTimingValues() {
        StreamRefreshProperties properties =
                new StreamRefreshProperties(Duration.ofSeconds(1), 10, Duration.ofHours(1), Duration.ofSeconds(5), 2);

        assertThat(properties.refreshAhead()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.refreshMinValidity()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void streamProxyPropertiesDefaults() {
        StreamProxyProperties properties = new StreamProxyProperties(Duration.ofSeconds(5), Duration.ofSeconds(15));
        assertThat(properties.proxyConnectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.proxyReadTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void streamProxyPropertiesRejectsNegativeOrZeroDurations() {
        assertThatThrownBy(() -> new StreamProxyProperties(Duration.ZERO, Duration.ofSeconds(15)))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new StreamProxyProperties(Duration.ofSeconds(5), Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bindingWithValidationFromEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("gravifon.library.music-root", "/custom/music");
        env.setProperty("gravifon.library.startup-scan-enabled", "false");
        env.setProperty("gravifon.streams.refresh-ahead", "PT30M");
        env.setProperty("gravifon.streams.refresh-upcoming-count", "4");
        env.setProperty("gravifon.streams.refresh-min-validity", "PT30S");
        env.setProperty("gravifon.streams.refresh-timeout", "PT2S");
        env.setProperty("gravifon.streams.refresh-max-attempts", "3");
        env.setProperty("gravifon.streams.proxy-connect-timeout", "PT10S");
        env.setProperty("gravifon.streams.proxy-read-timeout", "PT20S");

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        Binder binder = new Binder(ConfigurationPropertySources.get(env), null, null, null, null);

        LibraryProperties libProps = binder.bind("gravifon.library", LibraryProperties.class).get();
        assertThat(libProps.musicRoot()).isEqualTo(Path.of("/custom/music"));
        assertThat(libProps.startupScanEnabled()).isFalse();

        StreamRefreshProperties refreshProps = binder.bind("gravifon.streams", StreamRefreshProperties.class).get();
        assertThat(refreshProps.refreshAhead()).isEqualTo(Duration.ofMinutes(30));
        assertThat(refreshProps.refreshUpcomingCount()).isEqualTo(4);
        assertThat(refreshProps.refreshMinValidity()).isEqualTo(Duration.ofSeconds(30));
        assertThat(refreshProps.refreshTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(refreshProps.refreshMaxAttempts()).isEqualTo(3);

        StreamProxyProperties proxyProps = binder.bind("gravifon.streams", StreamProxyProperties.class).get();
        assertThat(proxyProps.proxyConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
        assertThat(proxyProps.proxyReadTimeout()).isEqualTo(Duration.ofSeconds(20));
    }
}
