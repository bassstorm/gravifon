package com.gravifon.player.api.model;

import com.gravifon.player.catalog.model.Track;

public record TrackResponse(
        String id,
        String filename,
        String format,
        Long durationSeconds
) {
    public static TrackResponse from(Track track) {
        return new TrackResponse(track.id(), track.filename(), track.format(), track.durationSeconds());
    }
}

