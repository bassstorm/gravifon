package com.gravifon.player.registry.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.persistence.TrackEntity;
import com.gravifon.player.persistence.TrackMetadataEntity;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackErrorReporter;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TrackConverterTest {
    @Test
    void fileTrackRoundTripPreservesMetadataOrderingAndErrorState() {
        Map<String, List<String>> metadata = new LinkedHashMap<>();
        metadata.put("TITLE", List.of("First", "Second"));
        metadata.put("ARTIST", List.of("Artist"));
        TrackError error =
                new TrackError(
                        "READ_ERROR",
                        "Unable to read file",
                        Instant.ofEpochSecond(1200),
                        TrackErrorReporter.SCAN
        );
        Track track = new FileTrack(
                "file-1",
                new TrackMetadata(metadata),
                180L,
                new TrackState(true, error),
                Path.of("music/song.mp3"),
                "mp3"
        );

        TrackEntity entity = TrackConverter.toEntity(track);
        Track converted = TrackConverter.toDomain(entity);

        assertThat(entity.metadata()).extracting(TrackMetadataEntity::key).containsExactly("TITLE", "TITLE", "ARTIST");
        assertThat(converted).isEqualTo(track);
        assertThat(converted.metadata().values()).containsEntry("TITLE", List.of("First", "Second"));
        assertThat(converted.state().lastError()).isEqualTo(error);
        assertThat(entity.errorAt()).isEqualTo(1200L);
    }

    @Test
    void streamTrackRoundTripHandlesNullableExpiryAndErrorFields() {
        Track track = new StreamTrack(
                "stream-1",
                new TrackMetadata(Map.of()),
                null,
                TrackState.healthy(),
                URI.create("https://source.example/track"),
                URI.create("https://cdn.example/track"),
                null
        );

        TrackEntity entity = TrackConverter.toEntity(track);
        Track converted = TrackConverter.toDomain(entity);

        assertThat(entity.expiresAfter()).isNull();
        assertThat(entity.errorKind()).isNull();
        assertThat(entity.errorAt()).isNull();
        assertThat(converted).isEqualTo(track);
        assertThat(((StreamTrack) converted).expiresAfter()).isNull();
        assertThat(converted.state()).isEqualTo(TrackState.healthy());
    }

    @Test
    void streamExpiryConvertsBetweenEpochSecondsAndInstant() {
        Instant expiry = Instant.ofEpochSecond(2400);
        Track track = new StreamTrack(
                "stream-2",
                new TrackMetadata(Map.of()),
                60L,
                TrackState.healthy(),
                URI.create("https://source.example/track"),
                URI.create("https://cdn.example/track"),
                expiry
        );

        TrackEntity entity = TrackConverter.toEntity(track);
        Track converted = TrackConverter.toDomain(entity);

        assertThat(entity.expiresAfter()).isEqualTo(2400L);
        assertThat(((StreamTrack) converted).expiresAfter()).isEqualTo(expiry);
    }
}
