package com.gravifon.player.registry.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.gravifon.player.config.LibraryProperties;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StartupScanTriggerTest {
    @Test
    void scanIsInvokedAndOutcomeRecordedWhenEnabled() {
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        LibraryProperties properties = new LibraryProperties(Path.of("/music"), true);
        ScanOutcomeTracker outcomeTracker = new ScanOutcomeTracker();
        StartupScanTrigger trigger = new StartupScanTrigger(coordinator, properties, outcomeTracker);

        trigger.onApplicationReady();

        verify(coordinator).scan();
        assertThat(outcomeTracker.getLatestOutcome()).isPresent();
        assertThat(outcomeTracker.getLatestOutcome().get().success()).isTrue();
        assertThat(outcomeTracker.getLatestOutcome().get().errorDetail()).isNull();
    }

    @Test
    void scanIsNotInvokedWhenDisabled() {
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        LibraryProperties properties = new LibraryProperties(Path.of("/music"), false);
        ScanOutcomeTracker outcomeTracker = new ScanOutcomeTracker();
        StartupScanTrigger trigger = new StartupScanTrigger(coordinator, properties, outcomeTracker);

        trigger.onApplicationReady();

        verify(coordinator, never()).scan();
        assertThat(outcomeTracker.getLatestOutcome()).isEmpty();
    }

    @Test
    void scanFailureIsRecordedAsFailedOutcomeWithoutThrowing() {
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        doThrow(new RuntimeException("Disk error")).when(coordinator).scan();
        LibraryProperties properties = new LibraryProperties(Path.of("/music"), true);
        ScanOutcomeTracker outcomeTracker = new ScanOutcomeTracker();
        StartupScanTrigger trigger = new StartupScanTrigger(coordinator, properties, outcomeTracker);

        trigger.onApplicationReady();

        assertThat(outcomeTracker.getLatestOutcome()).isPresent();
        assertThat(outcomeTracker.getLatestOutcome().get().success()).isFalse();
        assertThat(outcomeTracker.getLatestOutcome().get().errorDetail()).isEqualTo("Disk error");
    }
}
