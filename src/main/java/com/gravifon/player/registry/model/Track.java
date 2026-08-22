package com.gravifon.player.registry.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record Track(
        String id,
        TrackKind kind,
        Map<String, List<String>> metadata,
        Long durationSeconds,
        TrackState state,
        String relPath,
        String format,
        String sourceUrl,
        String streamUrl,
        Instant expiresAfter
) {

    public Track {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(state, "state");
        metadata = metadata.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
    }

    public boolean isStreamUrlFresh(Instant now) {
        return kind == TrackKind.STREAM && streamUrl != null
                && (expiresAfter == null || expiresAfter.isAfter(now));
    }
}