package com.gravifon.player.registry.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record FileTrack(
        String id,
        TrackMetadata trackMetadata,
        Long durationSeconds,
        TrackState state,
        String relPath,
        String format
) implements Track {
    public FileTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trackMetadata, "trackMetadata");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(relPath, "relPath");
    }

    public FileTrack(
            String id,
            Map<String, List<String>> metadata,
            Long durationSeconds,
            TrackState state,
            String relPath,
            String format
    ) {
        this(id, new TrackMetadata(metadata), durationSeconds, state, relPath, format);
    }

    public FileTrack(
            TrackId trackId,
            Map<String, List<String>> metadata,
            Long durationSeconds,
            TrackState state,
            String relPath,
            String format
    ) {
        this(trackId.value(), metadata, durationSeconds, state, relPath, format);
    }

    @Override
    public TrackKind kind() {
        return TrackKind.FILE;
    }

    @Override
    public FileTrack withState(TrackState newState) {
        return new FileTrack(id, trackMetadata, durationSeconds, newState, relPath, format);
    }

    @Override
    public FileTrack withMetadata(TrackMetadata newMetadata) {
        return new FileTrack(id, newMetadata, durationSeconds, state, relPath, format);
    }
}
