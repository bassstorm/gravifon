package com.gravifon.player.registry.stream;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ApplicationRing
@Component
@RequiredArgsConstructor
public class StreamRefreshScheduler {

    private final StreamRefreshService refreshService;

    @Scheduled(fixedDelayString = "${gravifon.streams.refresh-interval:PT10M}", timeUnit = TimeUnit.MILLISECONDS)
    public void refreshUpcomingStreams() {
        refreshService.refreshUpcomingStreams();
    }
}
