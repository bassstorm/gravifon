package com.gravifon.player.registry.model;

import com.gravifon.player.registry.identity.SourceNormalizer;
import java.nio.file.Path;
import java.util.Objects;

public record FileTrack(
        String id,
        TrackMetadata metadata,
        Long durationSeconds,
        TrackState state,
        Path relPath,
        String format
) implements Track {
    public FileTrack {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Track id cannot be blank");
        }
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(state, "state");
        relPath = SourceNormalizer.normalize(relPath);
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
    public FileTrack withMetadata(TrackMetadata newMetadata) {
        return new FileTrack(id, newMetadata, durationSeconds, state, relPath, format);
    }
}
