package com.gravifon.player.playback.service;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.playback.repository.PlaybackStateRepository;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class PlaybackServiceTest {

    private PlaylistService playlistService;
    private TrackRepository trackRepository;
    private PlaybackStateRepository stateRepository;
    private TrackSelectorRegistry selectorRegistry;
    private PlaybackService playbackService;

    @BeforeEach
    void setUp() {
        playlistService = Mockito.mock(PlaylistService.class);
        trackRepository = Mockito.mock(TrackRepository.class);
        stateRepository = Mockito.mock(PlaybackStateRepository.class);
        when(stateRepository.find(Mockito.any())).thenReturn(Optional.empty());
        selectorRegistry = new TrackSelectorRegistry(List.of(
                new SequentialTrackSelector(),
                new RandomTrackSelector()
        ));

        Playlist active = new Playlist("all-tracks", "All Tracks", List.of("t1", "t2"));
        when(playlistService.getActive()).thenReturn(active);

        when(trackRepository.findById("t1")).thenReturn(Optional.of(track("t1", 120L)));
        when(trackRepository.findById("t2")).thenReturn(Optional.of(track("t2", 60L)));

        playbackService = new PlaybackService(playlistService, trackRepository, stateRepository, selectorRegistry);
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
        when(trackRepository.findById("t3")).thenReturn(Optional.of(track("t3", 90L)));
        when(playlistService.getActive()).thenReturn(second, second);
        when(playlistService.select("favorites")).thenReturn(second);

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
    void randomMode_selectsValidTrackFromActivePlaylist() {
        playbackService.setMode(PlaybackMode.RANDOM);

        var first = playbackService.nextTrack();
        var second = playbackService.nextTrack();

        assertThat(List.of("t1", "t2")).contains(first.currentTrackId());
        assertThat(List.of("t1", "t2")).contains(second.currentTrackId());
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

    @Test
    void persistentPlayback_usesAndWritesTheActivePlaylistsMode() {
        PlaylistService persistentPlaylists = Mockito.mock(PlaylistService.class);
        TrackRepository tracks = Mockito.mock(TrackRepository.class);
        PlaybackStateRepository states = Mockito.mock(PlaybackStateRepository.class);
        Playlist playlist = new Playlist("p1", "Playlist", List.of("t1"), PlaybackMode.RANDOM);
        when(persistentPlaylists.getActive()).thenReturn(playlist);
        when(persistentPlaylists.select("p1")).thenReturn(playlist);
        when(tracks.findById("t1")).thenReturn(Optional.of(new com.gravifon.player.registry.model.FileTrack(
                "t1", Map.of(), 120L, TrackState.healthy(), "t1.mp3", "mp3")));
        PlaybackService persistentPlayback = new PlaybackService(persistentPlaylists, tracks, states, selectorRegistry);

        assertThat(persistentPlayback.selectPlaylist("p1").playbackMode()).isEqualTo(PlaybackMode.RANDOM);
        persistentPlayback.setMode(PlaybackMode.SEQUENTIAL);

        Mockito.verify(persistentPlaylists).setMode("p1", PlaybackMode.SEQUENTIAL);
        assertThat(persistentPlayback.getState().playbackMode()).isEqualTo(PlaybackMode.SEQUENTIAL);
    }

    @Test
    void restoredPlayingState_becomesPausedAtPersistedPosition() {
        PlaybackStateRepository states = Mockito.mock(PlaybackStateRepository.class);
        PlaylistService persistentPlaylists = Mockito.mock(PlaylistService.class);
        TrackRepository tracks = Mockito.mock(TrackRepository.class);
        Playlist playlist = new Playlist("p1", "Playlist", List.of("t1"));
        when(states.find("default")).thenReturn(Optional.of(new com.gravifon.player.playback.model.PlaybackState(
                "p1", "t1", PlaybackMode.SEQUENTIAL, TransportState.PLAYING, 37)));
        when(persistentPlaylists.select("p1")).thenReturn(playlist);
        when(persistentPlaylists.getActive()).thenReturn(playlist);
        when(tracks.findById("t1")).thenReturn(Optional.of(track("t1", 120L)));

        PlaybackService restored = new PlaybackService(persistentPlaylists, tracks, states, selectorRegistry);
        restored.restoreState();

        assertThat(restored.getState().transportState()).isEqualTo(TransportState.PAUSED);
        assertThat(restored.getState().positionSeconds()).isEqualTo(37);
        Mockito.verify(persistentPlaylists).activate("p1");
    }

    private com.gravifon.player.registry.model.Track track(String id, long duration) {
        return new com.gravifon.player.registry.model.FileTrack(id,
                Map.of(), duration,
                com.gravifon.player.registry.model.TrackState.healthy(), id + ".mp3", "mp3");
    }
}
