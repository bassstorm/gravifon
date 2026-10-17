package com.gravifon.player.registry.stream;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.registry.model.Track;
import java.io.IOException;
import java.time.Instant;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StreamRefreshService {
    private final RefreshPlanner planner;
    private final StreamTrackRefresher refresher;
    private final StreamRefreshProperties properties;

    public void refreshDue(Collection<Track> upcomingTracks, Instant now) {
        for (var track : planner.plan(upcomingTracks, now, properties.refreshAhead())) {
            try {
                refresher.ensureValid(track, properties.refreshAhead());
            } catch (IOException | RuntimeException failure) {
                log.warn(
                        "Proactive stream refresh failed; continuing with other tracks: trackId={}",
                        track.id(),
                        failure
                );
            }
        }
    }
}
