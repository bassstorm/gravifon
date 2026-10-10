package com.gravifon.player.registry.scan;

import java.time.Instant;

public record ScanOutcome(boolean success, Instant completedAt, String errorDetail) {
    public static ScanOutcome success(Instant completedAt) {
        return new ScanOutcome(true, completedAt, null);
    }

    public static ScanOutcome failure(Instant completedAt, String errorDetail) {
        return new ScanOutcome(false, completedAt, errorDetail);
    }
}
