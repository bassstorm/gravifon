package com.gravifon.player.registry.model;

import java.time.Instant;

public record TrackError(
        String kind,
        String message,
        Instant at,
        String reportedBy
) {
}