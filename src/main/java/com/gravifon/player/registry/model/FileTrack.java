package com.gravifon.player.registry.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FileTrack(
        String id,
        Map<String, List<String>> metadata,
        Long durationSeconds,
        TrackState state,
        String relPath,
        String format
) implements Track {
    public FileTrack {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Track id cannot be blank");
        }
        metadata = new TrackMetadata(metadata).values();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(relPath, "relPath");
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
