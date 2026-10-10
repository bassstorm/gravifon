package com.gravifon.player.registry.scan;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("libraryScan")
@RequiredArgsConstructor
public class LibraryScanHealthIndicator implements HealthIndicator {
    private final ScanOutcomeTracker outcomeTracker;

    @Override
    public Health health() {
        return outcomeTracker
            .getLatestOutcome()
            .map(outcome -> {
                Health.Builder health =
                        Health
                    .up()
                    .withDetail("success", outcome.success())
                    .withDetail("completedAt", outcome.completedAt());
                if (outcome.errorDetail() != null) {
                    health.withDetail("error", outcome.errorDetail());
                }
                return health.build();
            })
            .orElseGet(() -> Health.up().withDetail("outcome", "not-run").build());
    }
}
