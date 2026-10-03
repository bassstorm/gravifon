package com.gravifon.player.registry.model;

import java.time.Instant;

public record TrackError(String kind, String message, Instant at, String reportedBy) {
    public TrackError {
        requireText(kind, "kind");
        requireText(message, "message");
        requireText(reportedBy, "reportedBy");
        if (at == null) {
            throw new IllegalArgumentException("Track error timestamp is required");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Track error " + field + " must not be blank");
        }
    }
}
