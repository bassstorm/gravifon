package com.gravifon.player.registry.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrackPolymorphismTest {

    @Test
    void fileTrackHasExplicitPropertiesAndWithers() {
        FileTrack track = new FileTrack(
                "f1", Map.of("TITLE", List.of("Song")), 120L, TrackState.healthy(), "music/song.mp3", "mp3");

        assertThat(track.id()).isEqualTo("f1");
        assertThat(track.trackId()).isEqualTo(TrackId.of("f1"));
        assertThat(track.getId()).isEqualTo(TrackId.of("f1"));
        assertThat(track.kind()).isEqualTo(TrackKind.FILE);
        assertThat(track.relPath()).isEqualTo("music/song.mp3");
        assertThat(track.format()).isEqualTo("mp3");

        FileTrack updated = track.withState(
                new TrackState(true, new TrackError("READ_ERROR", "Unreadable", Instant.now(), "SCAN")));
        assertThat(updated.state().failing()).isTrue();
        assertThat(updated.metadata()).containsEntry("TITLE", List.of("Song"));
    }

    @Test
    void streamTrackEvaluatesFreshnessAndWithers() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        StreamTrack fresh = new StreamTrack(
                "s1",
                Map.of(),
                200L,
                TrackState.healthy(),
                "https://source/1",
                "https://stream/1",
                now.plusSeconds(60));
        StreamTrack expired = new StreamTrack(
                "s2",
                Map.of(),
                200L,
                TrackState.healthy(),
                "https://source/2",
                "https://stream/2",
                now.minusSeconds(1));
        StreamTrack noExpiry = new StreamTrack(
                "s3", Map.of(), 200L, TrackState.healthy(), "https://source/3", "https://stream/3", null);

        assertThat(fresh.isStreamUrlFresh(now)).isTrue();
        assertThat(expired.isStreamUrlFresh(now)).isFalse();
        assertThat(noExpiry.isStreamUrlFresh(now)).isTrue();

        StreamTrack refreshed = expired.withStream("https://stream/fresh", now.plusSeconds(300));
        assertThat(refreshed.streamUrl()).isEqualTo("https://stream/fresh");
        assertThat(refreshed.isStreamUrlFresh(now)).isTrue();
    }
}
