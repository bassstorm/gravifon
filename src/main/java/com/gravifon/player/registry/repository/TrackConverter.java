package com.gravifon.player.registry.repository;

import com.gravifon.player.persistence.TrackEntity;
import com.gravifon.player.persistence.TrackMetadataEntity;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

final class TrackConverter {
    private TrackConverter() {
    }

    static Track toDomain(TrackEntity entity) {
        var metadata = new LinkedHashMap<String, List<String>>();
        entity
            .metadata()
            .stream()
            .sorted(java.util.Comparator
                .comparing(TrackMetadataEntity::key)
                .thenComparingInt(TrackMetadataEntity::order)
            )
            .forEach(value -> metadata
                .computeIfAbsent(value.key(), ignored -> new ArrayList<>())
                .add(value.value()));
        TrackError error = entity.errorKind() == null
                ? null
                : TrackError.rehydrate(
                        entity.errorKind(),
                        entity.errorMessage(),
                        instant(entity.errorAt()),
                        entity.errorReporter()
        );
        TrackKind kind = TrackKind.valueOf(entity.kind());
        return switch (kind) {
            case FILE -> new FileTrack(
                    entity.id(),
                    metadata,
                    entity.durationSeconds(),
                    new TrackState(entity.failing(), error),
                    entity.relativePath(),
                    entity.format()
            );
            case STREAM -> new StreamTrack(
                    entity.id(),
                    metadata,
                    entity.durationSeconds(),
                    new TrackState(entity.failing(), error),
                    entity.sourceUrl(),
                    entity.streamUrl(),
                    instant(entity.expiresAfter())
            );
        };
    }

    static TrackEntity toEntity(Track track) {
        String relativePath = null;
        String format = null;
        String sourceUrl = null;
        String streamUrl = null;
        Long expiresAfter = null;
        if (track instanceof FileTrack fileTrack) {
            relativePath = fileTrack.relPath();
            format = fileTrack.format();
        } else if (track instanceof StreamTrack streamTrack) {
            sourceUrl = streamTrack.sourceUrl();
            streamUrl = streamTrack.streamUrl();
            expiresAfter = epoch(streamTrack.expiresAfter());
        }

        TrackError error = track.state().lastError();
        TrackEntity entity = new TrackEntity(
                track.id(),
                track.kind().name(),
                track.durationSeconds(),
                relativePath,
                format,
                sourceUrl,
                streamUrl,
                expiresAfter,
                track.state().failing(),
                error == null ? null : error.kind(),
                error == null ? null : error.message(),
                error == null ? null : epoch(error.at()),
                error == null ? null : error.reportedBy().name()
        );
        List<TrackMetadataEntity> metadata = new ArrayList<>();
        track.metadata().forEach((key, values) -> {
            for (int order = 0; order < values.size(); order++) {
                metadata.add(new TrackMetadataEntity(key, values.get(order), order));
            }
        });
        entity.replaceMetadata(metadata);
        return entity;
    }

    private static Long epoch(Instant value) {
        return value == null ? null : value.getEpochSecond();
    }

    private static Instant instant(Long value) {
        return value == null ? null : Instant.ofEpochSecond(value);
    }
}
