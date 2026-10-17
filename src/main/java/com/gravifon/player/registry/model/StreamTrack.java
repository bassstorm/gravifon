package com.gravifon.player.registry.model;

import com.gravifon.player.registry.identity.SourceNormalizer;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record StreamTrack(
        String id,
        TrackMetadata metadata,
        Long durationSeconds,
        TrackState state,
        URI sourceUrl,
        URI streamUrl,
        Instant expiresAfter
) implements Track {
    public StreamTrack {
        Objects.requireNonNull(id, "id");
        if (id.isBlank()) {
            throw new IllegalArgumentException("Track id cannot be blank");
        }
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(state, "state");
        sourceUrl = SourceNormalizer.normalize(sourceUrl);
        if (streamUrl != null
                && (streamUrl.getScheme() == null
                || streamUrl.getScheme().isBlank()
                || streamUrl.getHost() == null
                || streamUrl.getHost().isBlank())) {
            throw new IllegalArgumentException("Stream URL must include a scheme and host");
        }
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

    public StreamTrack withStream(URI newStreamUrl, Instant newExpiresAfter) {
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
    public StreamTrack withMetadata(TrackMetadata newMetadata) {
        return new StreamTrack(id, newMetadata, durationSeconds, state, sourceUrl, streamUrl, expiresAfter);
    }
}
