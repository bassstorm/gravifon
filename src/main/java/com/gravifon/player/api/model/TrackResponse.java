package com.gravifon.player.api.model;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record TrackResponse(
        String id,
        String filename,
        TrackKind kind,
        Map<String, List<String>> metadata,
        String format,
        Long durationSeconds,
        TrackState state,
        String sourceUrl,
        Instant expiresAfter
) {
    public static TrackResponse from(Track track) {
        String filename = track.relPath() == null ? null : java.nio.file.Path.of(track.relPath()).getFileName().toString();
        return new TrackResponse(track.id(), filename, track.kind(), track.metadata(), track.format(), track.durationSeconds(),
            track.state(), track.sourceUrl(), track.expiresAfter());
    }
}

