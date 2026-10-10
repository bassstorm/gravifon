package com.gravifon.player.streaming;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.stream.StreamRefreshService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class StreamRefreshSchedulerTest {
    @Test
    void scheduledExecutionFetchesUpcomingTracksAndDelegatesWithCurrentTime() {
        PlaybackService playbackService = mock(PlaybackService.class);
        StreamRefreshService refreshService = mock(StreamRefreshService.class);
        StreamRefreshProperties properties =
                new StreamRefreshProperties(Duration.ofHours(1), 10, Duration.ofMinutes(1), Duration.ofSeconds(5), 2);
        List<Track> upcoming =
                List.of(new FileTrack("t1", java.util.Map.of(), null, TrackState.healthy(), "t1.mp3", "mp3"));
        when(playbackService.upcomingTracks(10)).thenReturn(upcoming);
        StreamRefreshScheduler scheduler = new StreamRefreshScheduler(playbackService, refreshService, properties);

        scheduler.refreshUpcomingStreams();

        verify(playbackService).upcomingTracks(10);
        ArgumentCaptor<Instant> now = org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(refreshService).refreshDue(org.mockito.Mockito.same(upcoming), now.capture());
        org.assertj.core.api.Assertions.assertThat(now.getValue()).isNotNull();
    }
}
