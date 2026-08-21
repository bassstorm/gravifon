package com.gravifon.player.playback.service;

import com.gravifon.player.catalog.model.Track;
import com.gravifon.player.catalog.service.MediaCatalogService;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.InMemoryPlaylistService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class PlaybackServiceTest {

    private InMemoryPlaylistService playlistService;
    private MediaCatalogService catalogService;
    private Random random;
    private PlaybackService playbackService;

    @BeforeEach
    void setUp() {
        playlistService = Mockito.mock(InMemoryPlaylistService.class);
        catalogService = Mockito.mock(MediaCatalogService.class);
        random = Mockito.mock(Random.class);

        Playlist active = new Playlist("all-tracks", "All Tracks", List.of("t1", "t2"));
        when(playlistService.getActivePlaylist()).thenReturn(active);

        when(catalogService.findTrackById("t1")).thenReturn(Optional.of(new Track("t1", Path.of("/music/t1.mp3"), "t1.mp3", "mp3", 120L)));
        when(catalogService.findTrackById("t2")).thenReturn(Optional.of(new Track("t2", Path.of("/music/t2.mp3"), "t2.mp3", "mp3", 60L)));

        playbackService = new PlaybackService(playlistService, catalogService, random);
    }

    @Test
    void transportTransitions_keepServerPlaybackContextConsistent() {
        var playing = playbackService.setTransportState(TransportState.PLAYING);
        assertThat(playing.currentTrackId()).isEqualTo("t1");
        assertThat(playing.transportState()).isEqualTo(TransportState.PLAYING);

        var paused = playbackService.setTransportState(TransportState.PAUSED);
        assertThat(paused.currentTrackId()).isEqualTo("t1");
        assertThat(paused.transportState()).isEqualTo(TransportState.PAUSED);

        var stopped = playbackService.setTransportState(TransportState.STOPPED);
        assertThat(stopped.currentTrackId()).isEqualTo("t1");
        assertThat(stopped.positionSeconds()).isZero();
    }

    @Test
    void selectTrack_requiresTrackFromActivePlaylist_andResetsPosition() {
        playbackService.setTransportState(TransportState.PLAYING);
        playbackService.observeStream("t1", 59, 120);

        var selected = playbackService.selectTrack("t2");
        assertThat(selected.currentTrackId()).isEqualTo("t2");
        assertThat(selected.positionSeconds()).isZero();

        assertThatThrownBy(() -> playbackService.selectTrack("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active playlist");
    }

    @Test
    void selectPlaylist_eagerlyRepicksTrackFromNewActivePlaylist() {
        Playlist second = new Playlist("favorites", "Favorites", List.of("t3"));
        when(catalogService.findTrackById("t3"))
                .thenReturn(Optional.of(new Track("t3", Path.of("/music/t3.mp3"), "t3.mp3", "mp3", 90L)));
        when(playlistService.getActivePlaylist()).thenReturn(second, second);

        var selected = playbackService.selectPlaylist("favorites");

        assertThat(selected.activePlaylistId()).isEqualTo("favorites");
        assertThat(selected.currentTrackId()).isEqualTo("t3");
    }

    @Test
    void sequentialMode_advancesAndWrapsByOrder() {
        playbackService.setTransportState(TransportState.PLAYING);

        var second = playbackService.nextTrack();
        assertThat(second.currentTrackId()).isEqualTo("t2");

        var wrapped = playbackService.nextTrack();
        assertThat(wrapped.currentTrackId()).isEqualTo("t1");
    }

    @Test
    void randomMode_selectsPureRandomEachStep() {
        when(random.nextInt(2)).thenReturn(1, 1);

        playbackService.setMode(PlaybackMode.RANDOM);

        var first = playbackService.nextTrack();
        var second = playbackService.nextTrack();

        assertThat(first.currentTrackId()).isEqualTo("t2");
        assertThat(second.currentTrackId()).isEqualTo("t2");
    }

    @Test
    void positionReport_acceptsCurrentTrackOnlyWithinObservedPlayback() {
        playbackService.setTransportState(TransportState.PLAYING);
        playbackService.observeStream("t1", 59, 120);

        assertThat(playbackService.reportPosition("t1", 42).positionSeconds()).isEqualTo(42);
        assertThat(playbackService.reportPosition("t1", 100).positionSeconds()).isEqualTo(42);
        assertThat(playbackService.reportPosition("stale", 42).positionSeconds()).isEqualTo(42);
    }

    @Test
    void streamObservation_isIgnoredForNonCurrentTrack_andFallbackTracksHighestPosition() {
        playbackService.setTransportState(TransportState.PLAYING);
        playbackService.observeStream("t2", 59, 60);
        assertThat(playbackService.getState().positionSeconds()).isZero();

        playbackService.observeStream("t1", 59, 120);
        assertThat(playbackService.getState().positionSeconds()).isEqualTo(60);
    }

    @Test
    void plausiblePositionReport_isPreservedAfterLaterStreamObservation() {
        playbackService.setTransportState(TransportState.PLAYING);
        playbackService.observeStream("t1", 59, 120);
        playbackService.reportPosition("t1", 42);

        playbackService.observeStream("t1", 119, 120);

        assertThat(playbackService.getState().positionSeconds()).isEqualTo(42);
    }
}
