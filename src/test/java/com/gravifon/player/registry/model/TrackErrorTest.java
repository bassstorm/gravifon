package com.gravifon.player.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TrackErrorTest {
    @Test
    void requiresCompleteNonBlankErrorDetails() {
        Instant now = Instant.now();

        assertThatThrownBy(() -> new TrackError(" ", "message", now, TrackErrorReporter.CLIENT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("kind");
        assertThatThrownBy(() -> new TrackError("CLIENT_ERROR", " ", now, TrackErrorReporter.CLIENT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("message");
        assertThatThrownBy(() -> new TrackError("CLIENT_ERROR", "message", now, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reportedBy");
        assertThatThrownBy(() -> new TrackError("CLIENT_ERROR", "message", null, TrackErrorReporter.CLIENT))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("timestamp");
    }

    @Test
    void namedFactoriesAssignReporterAndPreservePersistedNames() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(TrackError.readFailure("song.mp3", now).reportedBy()).isEqualTo(TrackErrorReporter.SCAN);
        assertThat(TrackError.streamRefreshFailure("STREAM_UNREACHABLE", "Unavailable", now).reportedBy())
            .isEqualTo(TrackErrorReporter.STREAM_REFRESH);
        assertThat(TrackError.clientFailure(null, "Reported", now))
            .extracting(TrackError::kind, TrackError::reportedBy)
            .containsExactly("CLIENT_ERROR", TrackErrorReporter.CLIENT);
        assertThat(TrackError.rehydrate("READ_ERROR", "Unreadable", now, "SCAN").reportedBy().name()).isEqualTo("SCAN");
    }
}
