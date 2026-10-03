package com.gravifon.player.registry.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record StreamTrack(
        String id,
        TrackMetadata trackMetadata,
        Long durationSeconds,
        TrackState state,
        String sourceUrl,
        String streamUrl,
        Instant expiresAfter
) implements Track {
    public StreamTrack {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(trackMetadata, "trackMetadata");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
    }

    public StreamTrack(
            String id,
            Map<String, List<String>> metadata,
            Long durationSeconds,
            TrackState state,
            String sourceUrl,
            String streamUrl,
            Instant expiresAfter
    ) {
        this(id, new TrackMetadata(metadata), durationSeconds, state, sourceUrl, streamUrl, expiresAfter);
    }

    public StreamTrack(
            TrackId trackId,
            Map<String, List<String>> metadata,
            Long durationSeconds,
            TrackState state,
            String sourceUrl,
            String streamUrl,
            Instant expiresAfter
    ) {
        this(trackId.value(), metadata, durationSeconds, state, sourceUrl, streamUrl, expiresAfter);
    }

    @Override
    public TrackKind kind() {
        return TrackKind.STREAM;
    }

    public boolean isStreamUrlFresh(Instant now) {
        return streamUrl != null && (expiresAfter == null || expiresAfter.isAfter(now));
    }

    public StreamTrack withStream(String newStreamUrl, Instant newExpiresAfter) {
        return new StreamTrack(
                id,
                trackMetadata,
                durationSeconds,
                TrackState.healthy(),
                sourceUrl,
                newStreamUrl,
                newExpiresAfter
        );
    }

    @Override
    public StreamTrack withState(TrackState newState) {
        return new StreamTrack(id, trackMetadata, durationSeconds, newState, sourceUrl, streamUrl, expiresAfter);
    }

    @Override
    public StreamTrack withMetadata(TrackMetadata newMetadata) {
        return new StreamTrack(id, newMetadata, durationSeconds, state, sourceUrl, streamUrl, expiresAfter);
    }
}
