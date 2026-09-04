package com.gravifon.player.registry.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record FileTrack(
        String id,
        Map<String, List<String>> metadata,
        Long durationSeconds,
        TrackState state,
        String relPath,
        String format)
        implements Track {

    public FileTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(relPath, "relPath");
        metadata = metadata.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    public FileTrack(
            TrackId trackId,
            Map<String, List<String>> metadata,
            Long durationSeconds,
            TrackState state,
            String relPath,
            String format) {
        this(trackId.value(), metadata, durationSeconds, state, relPath, format);
    }

    @Override
    public TrackKind kind() {
        return TrackKind.FILE;
    }

    @Override
    public FileTrack withState(TrackState newState) {
        return new FileTrack(id, metadata, durationSeconds, newState, relPath, format);
    }

    @Override
    public FileTrack withMetadata(Map<String, List<String>> newMetadata) {
        return new FileTrack(id, newMetadata, durationSeconds, state, relPath, format);
    }
}
