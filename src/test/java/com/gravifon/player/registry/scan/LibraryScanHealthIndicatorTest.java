package com.gravifon.player.registry.scan;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

class LibraryScanHealthIndicatorTest {
    @Test
    void reportsSuccessAndFailureDetailsWithoutDegradingStatus() {
        ScanOutcomeTracker tracker = new ScanOutcomeTracker();
        LibraryScanHealthIndicator indicator = new LibraryScanHealthIndicator(tracker);
        Instant completedAt = Instant.parse("2026-01-01T00:00:00Z");

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        tracker.record(ScanOutcome.success(completedAt));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("success", true).containsEntry(
                "completedAt",
                completedAt
        );

        tracker.record(ScanOutcome.failure(completedAt, "permission denied"));
        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails())
            .containsEntry("success", false)
            .containsEntry("completedAt", completedAt)
            .containsEntry("error", "permission denied");
    }
}
