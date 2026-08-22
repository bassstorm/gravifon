package com.gravifon.player.registry.stream;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamRefreshSchedulerTest {
    private final PlaylistService playlists = mock(PlaylistService.class);
    private final TrackRepository tracks = mock(TrackRepository.class);
    private final StreamRefreshService refresh = mock(StreamRefreshService.class);
    private final PlaybackService playback = mock(PlaybackService.class);
    private final GravifonProperties properties = new GravifonProperties();
        private final StreamRefreshScheduler scheduler = new StreamRefreshScheduler(playlists, tracks, refresh, properties,
            playback,
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    {
        properties.getStreams().setRefreshAhead(Duration.ofMinutes(5));
    }

    @Test
    void refreshesExpiringStreamInActivePlaylist() throws Exception {
        Track track = track("t1", Instant.parse("2026-01-01T00:02:00Z"));
        when(playlists.getActive()).thenReturn(new Playlist("p1", "Active", List.of("t1", "t2")));
        when(playback.getState()).thenReturn(
            new PlaybackState("p1", "t1", PlaybackMode.SEQUENTIAL, TransportState.PLAYING, 0));
        when(tracks.findById("t1")).thenReturn(Optional.of(track));

        scheduler.refreshScope();

        verify(refresh).ensureFresh(track);
    }

    @Test
    void ignoresHealthyAndOutOfScopeTracks() throws Exception {
        Track outside = track("t3", Instant.parse("2026-01-01T00:02:00Z"));
        when(playlists.getActive()).thenReturn(new Playlist("p1", "Active", List.of("t1", "t2", "t3")));
        when(playback.getState()).thenReturn(
            new PlaybackState("p1", "t1", PlaybackMode.SEQUENTIAL, TransportState.PLAYING, 0));
        when(tracks.findById("t3")).thenReturn(Optional.of(outside));

        scheduler.refreshScope();

        verify(refresh, never()).ensureFresh(outside);
    }

    private Track track(String id, Instant expiresAfter) {
        return new Track(id, TrackKind.STREAM, Map.of(), null, TrackState.healthy(), null, "mp3",
                "https://source", "https://stream", expiresAfter);
    }
}
