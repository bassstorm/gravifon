package com.gravifon.player.registry.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrackPolymorphismTest {
    @Test
    void tracksRequireNonBlankIds() {
        TrackMetadata emptyMetadata = new TrackMetadata(Map.of());
        assertThatThrownBy(() -> new FileTrack(
                null,
                emptyMetadata,
                1L,
                TrackState.healthy(),
                Path.of("song.mp3"),
                "mp3"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("id");
        assertThatThrownBy(() -> new FileTrack(" ", emptyMetadata, 1L, TrackState.healthy(), Path.of("song.mp3"), "mp3"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Track id cannot be blank");
        assertThatThrownBy(() -> new StreamTrack(
                null,
                emptyMetadata,
                1L,
                TrackState.healthy(),
                URI.create("https://source"),
                null,
                null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("id");
        assertThatThrownBy(() -> new StreamTrack(
                " ",
                emptyMetadata,
                1L,
                TrackState.healthy(),
                URI.create("https://source"),
                null,
                null
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Track id cannot be blank");
    }

    @Test
    void fileTrackHasExplicitPropertiesAndWithers() {
        FileTrack track = new FileTrack(
                "f1",
                new TrackMetadata(Map.of("TITLE", List.of("Song"))),
                120L,
                TrackState.healthy(),
                Path.of("music/song.mp3"),
                "mp3"
        );

        assertThat(track.id()).isEqualTo("f1");
        assertThat(track.kind()).isEqualTo(TrackKind.FILE);
        assertThat(track.relPath()).isEqualTo(Path.of("music/song.mp3"));
        assertThat(track.format()).isEqualTo("mp3");

        FileTrack updated = track.withState(new TrackState(true, TrackError.readFailure("Unreadable", Instant.now())));
        assertThat(updated.state().failing()).isTrue();
        assertThat(updated.metadata().values()).containsEntry("TITLE", List.of("Song"));
    }

    @Test
    void streamTrackEvaluatesRefreshNeedAndWithers() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        StreamTrack fresh = new StreamTrack(
                "s1",
                new TrackMetadata(Map.of()),
                200L,
                TrackState.healthy(),
                URI.create("https://source/1"),
                URI.create("https://stream/1"),
                now.plusSeconds(60)
        );
        StreamTrack expired = new StreamTrack(
                "s2",
                new TrackMetadata(Map.of()),
                200L,
                TrackState.healthy(),
                URI.create("https://source/2"),
                URI.create("https://stream/2"),
                now.minusSeconds(1)
        );
        StreamTrack noExpiry = new StreamTrack(
                "s3",
                new TrackMetadata(Map.of()),
                200L,
                TrackState.healthy(),
                URI.create("https://source/3"),
                URI.create("https://stream/3"),
                null
        );

        assertThat(fresh.needsRefresh(now, Duration.ZERO)).isFalse();
        assertThat(fresh.needsRefresh(now, Duration.ofSeconds(60))).isTrue();
        assertThat(expired.needsRefresh(now, Duration.ZERO)).isTrue();
        assertThat(noExpiry.needsRefresh(now, Duration.ofDays(1))).isFalse();

        StreamTrack missingUrl = new StreamTrack(
                "s4",
                new TrackMetadata(Map.of()),
                200L,
                TrackState.healthy(),
                URI.create("https://source/4"),
                null,
                null
        );
        assertThat(missingUrl.needsRefresh(now, Duration.ZERO)).isTrue();

        StreamTrack refreshed = expired.withStream(URI.create("https://stream/fresh"), now.plusSeconds(300));
        assertThat(refreshed.streamUrl()).isEqualTo(URI.create("https://stream/fresh"));
        assertThat(refreshed.needsRefresh(now, Duration.ZERO)).isFalse();
    }

    @Test
    void withMetadataRetainsDomainValueWithoutReconstruction() {
        FileTrack original = new FileTrack(
                "f1",
                new TrackMetadata(Map.of("TITLE", List.of("Song"))),
                120L,
                TrackState.healthy(),
                Path.of("music/song.mp3"),
                "mp3"
        );
        TrackMetadata metadata = new TrackMetadata(Map.of("ARTIST", List.of("Artist")));

        FileTrack updated = original.withMetadata(metadata);

        assertThat(updated.metadata().values()).containsOnlyKeys("ARTIST").containsEntry("ARTIST", List.of("Artist"));
    }

    @Test
    void tracksNormalizeAndValidateTheirSourceTypes() {
        FileTrack file = new FileTrack(
                "f1",
                new TrackMetadata(Map.of()),
                null,
                TrackState.healthy(),
                Path.of("music/./song.mp3"),
                "mp3"
        );
        StreamTrack stream = new StreamTrack(
                "s1",
                new TrackMetadata(Map.of()),
                null,
                TrackState.healthy(),
                URI.create("HTTPS://Example.COM/song?utm_source=test"),
                null,
                null
        );

        assertThat(file.relPath()).isEqualTo(Path.of("music/song.mp3"));
        assertThat(stream.sourceUrl()).isEqualTo(URI.create("https://example.com/song"));
        assertThatThrownBy(() -> new FileTrack(
                "f2",
                new TrackMetadata(Map.of()),
                null,
                TrackState.healthy(),
                Path.of("../outside.mp3"),
                "mp3"
        ))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StreamTrack(
                "s2",
                new TrackMetadata(Map.of()),
                null,
                TrackState.healthy(),
                URI.create("https://source"),
                URI.create("/relative"),
                null
        ))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
