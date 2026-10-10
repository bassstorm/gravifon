package com.gravifon.player.registry.model;

import java.time.Instant;

public record TrackError(String kind, String message, Instant at, TrackErrorReporter reportedBy) {
    public TrackError {
        requireText(kind, "kind");
        requireText(message, "message");
        if (reportedBy == null) {
            throw new IllegalArgumentException("Track error reportedBy is required");
        }
        if (at == null) {
            throw new IllegalArgumentException("Track error timestamp is required");
        }
    }

    public static TrackError sourceMissing(String path, Instant at) {
        return new TrackError("SOURCE_MISSING", "File is missing: " + path, at, TrackErrorReporter.SCAN);
    }

    public static TrackError readFailure(String path, Instant at) {
        return new TrackError("READ_ERROR", "Unable to read file: " + path, at, TrackErrorReporter.SCAN);
    }

    public static TrackError streamRefreshFailure(String kind, String message, Instant at) {
        return new TrackError(kind, message, at, TrackErrorReporter.STREAM_REFRESH);
    }

    public static TrackError clientFailure(String kind, String message, Instant at) {
        return new TrackError(kind == null ? "CLIENT_ERROR" : kind, message, at, TrackErrorReporter.CLIENT);
    }

    public static TrackError rehydrate(String kind, String message, Instant at, String reportedBy) {
        return new TrackError(kind, message, at, TrackErrorReporter.valueOf(reportedBy));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Track error " + field + " must not be blank");
        }
    }
}
