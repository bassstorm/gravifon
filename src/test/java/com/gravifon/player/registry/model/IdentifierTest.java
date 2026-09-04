package com.gravifon.player.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gravifon.player.playlist.model.PlaylistId;
import org.junit.jupiter.api.Test;

class IdentifierTest {
    @Test
    void trackIdValidationAndEquality() {
        TrackId id1 = TrackId.of("track-123");
        TrackId id2 = new TrackId("track-123");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.value()).isEqualTo("track-123");
        assertThat(id1.toString()).isEqualTo("track-123");

        assertThatThrownBy(() -> TrackId.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> TrackId.of("  ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void playlistIdValidationAndEquality() {
        PlaylistId id1 = PlaylistId.of("playlist-abc");
        PlaylistId id2 = new PlaylistId("playlist-abc");

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.value()).isEqualTo("playlist-abc");
        assertThat(id1.toString()).isEqualTo("playlist-abc");

        assertThatThrownBy(() -> PlaylistId.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> PlaylistId.of("")).isInstanceOf(IllegalArgumentException.class);
    }
}
