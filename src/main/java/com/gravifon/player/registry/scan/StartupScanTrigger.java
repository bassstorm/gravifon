package com.gravifon.player.registry.scan;

import com.gravifon.player.config.LibraryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StartupScanTrigger {
    private final LibraryScanCoordinator scanCoordinator;
    private final LibraryProperties properties;
    private final ScanOutcomeTracker outcomeTracker;

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!properties.startupScanEnabled()) {
            log.info("Startup library scan is disabled");
            return;
        }
        log.info("Starting background library scan on application ready");
        try {
            scanCoordinator.scan();
            outcomeTracker.recordSuccess();
            log.info("Background library scan completed successfully");
        } catch (Exception e) {
            String error = e.getMessage() != null ? e.getMessage() : e.toString();
            outcomeTracker.recordFailure(error);
            log.error("Background library scan failed: {}", error, e);
        }
    }
}
