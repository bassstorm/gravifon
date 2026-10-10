package com.gravifon.player.registry.scan;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class ScanOutcomeTracker {
    private final AtomicReference<ScanOutcome> latestOutcome = new AtomicReference<>();

    public void recordSuccess() {
        record(ScanOutcome.success(java.time.Instant.now()));
    }

    public void recordFailure(String errorDetail) {
        record(ScanOutcome.failure(java.time.Instant.now(), errorDetail));
    }

    public void record(ScanOutcome outcome) {
        latestOutcome.set(outcome);
    }

    public Optional<ScanOutcome> getLatestOutcome() {
        return Optional.ofNullable(latestOutcome.get());
    }
}
