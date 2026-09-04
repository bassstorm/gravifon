package com.gravifon.player.registry.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        metadata = metadata
            .entrySet()
            .stream()
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
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
