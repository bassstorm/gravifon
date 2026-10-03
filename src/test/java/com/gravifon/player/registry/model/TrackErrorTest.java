package com.gravifon.player.registry.model;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrackErrorTest {
    @Test
    void requiresCompleteNonBlankErrorDetails() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> new TrackError(" ", "message", now, "CLIENT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kind");
        assertThatThrownBy(() -> new TrackError("CLIENT_ERROR", " ", now, "CLIENT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("message");
        assertThatThrownBy(() -> new TrackError("CLIENT_ERROR", "message", now, " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reportedBy");
        assertThatThrownBy(() -> new TrackError("CLIENT_ERROR", "message", null, "CLIENT"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timestamp");
    }
}
