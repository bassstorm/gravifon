package com.gravifon.player.api.model;

import com.gravifon.player.catalog.model.Track;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.model.Playlist;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiModelMappingTest {

    @Test
    void trackResponse_fromMapsFields() {
        Track track = new Track("track-1", Path.of("/music/a.mp3"), "a.mp3", "mp3", 42L);

        TrackResponse response = TrackResponse.from(track);

        assertThat(response.id()).isEqualTo("track-1");
        assertThat(response.filename()).isEqualTo("a.mp3");
        assertThat(response.format()).isEqualTo("mp3");
        assertThat(response.durationSeconds()).isEqualTo(42L);
    }

    @Test
    void playlistResponse_fromSetsActiveFlagByIdMatch() {
        Playlist playlist = new Playlist("p1", "Playlist", List.of("t1"));

        PlaylistResponse active = PlaylistResponse.from(playlist, "p1");
        PlaylistResponse inactive = PlaylistResponse.from(playlist, "other");

        assertThat(active.active()).isTrue();
        assertThat(inactive.active()).isFalse();
    }

    @Test
    void playbackStateResponse_fromMapsAndNormalizesEnumValues() {
        PlaybackState state = new PlaybackState("all-tracks", "t1", PlaybackMode.RANDOM, TransportState.PAUSED, 8);

        PlaybackStateResponse response = PlaybackStateResponse.from(state);

        assertThat(response.activePlaylistId()).isEqualTo("all-tracks");
        assertThat(response.currentTrackId()).isEqualTo("t1");
        assertThat(response.playbackMode()).isEqualTo("random");
        assertThat(response.transportState()).isEqualTo("paused");
        assertThat(response.positionSeconds()).isEqualTo(8);
    }
}

