package com.gravifon.player.registry.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StreamTrack(
        String id,
        Map<String, List<String>> metadata,
        Long durationSeconds,
        TrackState state,
        String sourceUrl,
        String streamUrl,
        Instant expiresAfter
) implements Track {
    public StreamTrack {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Track id cannot be blank");
        }
        metadata = new TrackMetadata(metadata).values();
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
    }

    @Override
    public TrackKind kind() {
        return TrackKind.STREAM;
    }

    public boolean needsRefresh(Instant now, Duration margin) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(margin, "margin");
        if (margin.isNegative()) {
            throw new IllegalArgumentException("Refresh margin must not be negative");
        }
        return streamUrl == null || expiresAfter != null && !expiresAfter.isAfter(now.plus(margin));
    }

    public StreamTrack withStream(String newStreamUrl, Instant newExpiresAfter) {
        return new StreamTrack(
                id,
                metadata,
                durationSeconds,
                TrackState.healthy(),
                sourceUrl,
                newStreamUrl,
                newExpiresAfter
        );
    }

    @Override
    public StreamTrack withState(TrackState newState) {
        return new StreamTrack(id, metadata, durationSeconds, newState, sourceUrl, streamUrl, expiresAfter);
    }

    @Override
    public StreamTrack withMetadata(Map<String, List<String>> newMetadata) {
        return new StreamTrack(id, newMetadata, durationSeconds, state, sourceUrl, streamUrl, expiresAfter);
    }
}
