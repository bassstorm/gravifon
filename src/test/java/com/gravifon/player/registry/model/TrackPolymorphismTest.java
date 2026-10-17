package com.gravifon.player.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrackPolymorphismTest {
    @Test
    void tracksRequireNonBlankIds() {
        assertThatThrownBy(() -> new FileTrack(null, Map.of(), 1L, TrackState.healthy(), "song.mp3", "mp3"))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("id");
        assertThatThrownBy(() -> new FileTrack(" ", Map.of(), 1L, TrackState.healthy(), "song.mp3", "mp3"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Track id cannot be blank");
        assertThatThrownBy(() -> new StreamTrack(null, Map.of(), 1L, TrackState.healthy(), "source", null, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("id");
        assertThatThrownBy(() -> new StreamTrack(" ", Map.of(), 1L, TrackState.healthy(), "source", null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Track id cannot be blank");
    }

    @Test
    void fileTrackHasExplicitPropertiesAndWithers() {
        FileTrack track =
                new FileTrack(
                        "f1",
                        Map.of("TITLE", List.of("Song")),
                        120L,
                        TrackState.healthy(),
                        "music/song.mp3",
                        "mp3"
        );

        assertThat(track.id()).isEqualTo("f1");
        assertThat(track.kind()).isEqualTo(TrackKind.FILE);
        assertThat(track.relPath()).isEqualTo("music/song.mp3");
        assertThat(track.format()).isEqualTo("mp3");

        FileTrack updated = track.withState(new TrackState(true, TrackError.readFailure("Unreadable", Instant.now())));
        assertThat(updated.state().failing()).isTrue();
        assertThat(updated.metadata()).containsEntry("TITLE", List.of("Song"));
    }

    @Test
    void streamTrackEvaluatesRefreshNeedAndWithers() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        StreamTrack fresh = new StreamTrack(
                "s1",
                Map.of(),
                200L,
                TrackState.healthy(),
                "https://source/1",
                "https://stream/1",
                now.plusSeconds(60)
        );
        StreamTrack expired = new StreamTrack(
                "s2",
                Map.of(),
                200L,
                TrackState.healthy(),
                "https://source/2",
                "https://stream/2",
                now.minusSeconds(1)
        );
        StreamTrack noExpiry =
                new StreamTrack(
                        "s3",
                        Map.of(),
                        200L,
                        TrackState.healthy(),
                        "https://source/3",
                        "https://stream/3",
                        null
        );

        assertThat(fresh.needsRefresh(now, Duration.ZERO)).isFalse();
        assertThat(fresh.needsRefresh(now, Duration.ofSeconds(60))).isTrue();
        assertThat(expired.needsRefresh(now, Duration.ZERO)).isTrue();
        assertThat(noExpiry.needsRefresh(now, Duration.ofDays(1))).isFalse();

        StreamTrack missingUrl =
                new StreamTrack("s4", Map.of(), 200L, TrackState.healthy(), "https://source/4", null, null);
        assertThat(missingUrl.needsRefresh(now, Duration.ZERO)).isTrue();

        StreamTrack refreshed = expired.withStream("https://stream/fresh", now.plusSeconds(300));
        assertThat(refreshed.streamUrl()).isEqualTo("https://stream/fresh");
        assertThat(refreshed.needsRefresh(now, Duration.ZERO)).isFalse();
    }

    @Test
    void withMetadataRetainsDomainValueWithoutReconstruction() {
        FileTrack original =
                new FileTrack(
                        "f1",
                        Map.of("TITLE", List.of("Song")),
                        120L,
                        TrackState.healthy(),
                        "music/song.mp3",
                        "mp3"
        );
        Map<String, List<String>> metadata = Map.of("ARTIST", List.of("Artist"));

        FileTrack updated = original.withMetadata(metadata);

        assertThat(updated.metadata()).containsOnlyKeys("ARTIST").containsEntry("ARTIST", List.of("Artist"));
    }
}
