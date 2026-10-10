package com.gravifon.player.streaming;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.registry.stream.StreamRefreshService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ApplicationRing
@Component
@RequiredArgsConstructor
public class StreamRefreshScheduler {
    private final PlaybackService playbackService;
    private final StreamRefreshService refreshService;
    private final StreamRefreshProperties properties;

    @Scheduled(cron = "${gravifon.streams.refresh-cron}")
    public void refreshUpcomingStreams() {
        refreshService.refreshDue(playbackService.upcomingTracks(properties.refreshUpcomingCount()), Instant.now());
    }
}
