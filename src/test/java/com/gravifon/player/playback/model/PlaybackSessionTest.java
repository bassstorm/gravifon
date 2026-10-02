package com.gravifon.player.playback.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gravifon.player.playback.service.SequentialTrackSelector;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaybackSessionTest {
    private final TrackSelector selector = new SequentialTrackSelector();
    private final List<String> trackIds = List.of("track-a", "track-b");

    @Test
    void selectingPlaylistAndTrackUpdatesSessionIdentityAndPosition() {
        PlaybackSession initial = PlaybackSession.initial("default");

        PlaybackSession selected = initial.selectPlaylist("playlist-1", PlaybackMode.SEQUENTIAL, trackIds, selector);
        PlaybackSession selectedTrack = selected.selectTrack("track-b", trackIds);

        assertThat(selected.activePlaylistId()).isEqualTo("playlist-1");
        assertThat(selected.currentTrackId()).isEqualTo("track-a");
        assertThat(selected.observedPositionSeconds()).isZero();
        assertThat(selectedTrack.currentTrackId()).isEqualTo("track-b");
        assertThat(selectedTrack.observedPositionSeconds()).isZero();
        assertThat(initial.activePlaylistId()).isNull();
    }

    @Test
    void selectingTrackOutsidePlaylistIsRejected() {
        PlaybackSession selected = selectedSession();

        assertThatThrownBy(() -> selected.selectTrack("missing", trackIds))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("active playlist");
    }

    @Test
    void nextTrackAdvancesWrapsAndClearsWhenPlaylistIsEmpty() {
        PlaybackSession selected = selectedSession();

        PlaybackSession next = selected.nextTrack(trackIds, selector);
        PlaybackSession wrapped = next.nextTrack(trackIds, selector);
        PlaybackSession empty = wrapped.nextTrack(List.of(), selector);

        assertThat(next.currentTrackId()).isEqualTo("track-b");
        assertThat(wrapped.currentTrackId()).isEqualTo("track-a");
        assertThat(empty.currentTrackId()).isNull();
        assertThat(empty.observedPositionSeconds()).isZero();
    }

    @Test
    void reportedPositionTakesPrecedenceOverLaterStreamObservations() {
        PlaybackSession observed = selectedSession().observeStream("track-a", 59, 120, 120L);
        PlaybackSession reported = observed.reportPosition("track-a", 42, 120L);
        PlaybackSession laterStream = reported.observeStream("track-a", 119, 120, 120L);

        assertThat(observed.observedPositionSeconds()).isEqualTo(60);
        assertThat(reported.toPlaybackState().positionSeconds()).isEqualTo(42);
        assertThat(reported.toPlaybackState().positionOrigin()).isEqualTo("REPORTED");
        assertThat(laterStream.observedPositionSeconds()).isEqualTo(120);
        assertThat(laterStream.toPlaybackState().positionSeconds()).isEqualTo(42);
    }

    @Test
    void positionReportsAndStreamObservationsRespectDurationBounds() {
        PlaybackSession selected = selectedSession();
        PlaybackSession observed = selected.observeStream("track-a", 150, 100, 80L);
        PlaybackSession tooLong = observed.reportPosition("track-a", 81, 80L);
        PlaybackSession atDuration = observed.reportPosition("track-a", 80, 80L);

        assertThat(observed.observedPositionSeconds()).isEqualTo(80);
        assertThat(tooLong).isSameAs(observed);
        assertThat(atDuration.toPlaybackState().positionSeconds()).isEqualTo(80);
        assertThatThrownBy(() -> selected.reportPosition("track-a", -1, 80L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(">= 0");
    }

    @Test
    void transportTransitionsSelectTrackPauseAndResetPositionOnStop() {
        PlaybackSession selected = selectedSession();
        PlaybackSession playing = selected.setTransportState(TransportState.PLAYING, trackIds, selector);
        PlaybackSession observed = playing.observeStream("track-a", 59, 120, 120L);
        PlaybackSession paused = observed.setTransportState(TransportState.PAUSED, trackIds, selector);
        PlaybackSession stopped = paused.setTransportState(TransportState.STOPPED, trackIds, selector);

        assertThat(playing.transportState()).isEqualTo(TransportState.PLAYING);
        assertThat(playing.currentTrackId()).isEqualTo("track-a");
        assertThat(paused.transportState()).isEqualTo(TransportState.PAUSED);
        assertThat(paused.toPlaybackState().positionSeconds()).isEqualTo(60);
        assertThat(stopped.transportState()).isEqualTo(TransportState.STOPPED);
        assertThat(stopped.currentTrackId()).isEqualTo("track-a");
        assertThat(stopped.toPlaybackState().positionSeconds()).isZero();
        assertThat(stopped.toPlaybackState().positionOrigin()).isEqualTo("OBSERVED");
        assertThatThrownBy(() -> selected.setTransportState(null, trackIds, selector))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void restoringPlayingStatePausesAndPreservesReportedPosition() {
        PlaybackState state =
                new PlaybackState("playlist-1", "track-a", PlaybackMode.RANDOM, TransportState.PLAYING, 37, "REPORTED");

        PlaybackSession restored = PlaybackSession.fromState("default", state);

        assertThat(restored.transportState()).isEqualTo(TransportState.PAUSED);
        assertThat(restored.playbackMode()).isEqualTo(PlaybackMode.RANDOM);
        assertThat(restored.reportedPositionSeconds()).isEqualTo(37);
        assertThat(restored.toPlaybackState().positionOrigin()).isEqualTo("REPORTED");
    }

    private PlaybackSession selectedSession() {
        return PlaybackSession
            .initial("default")
            .selectPlaylist("playlist-1", PlaybackMode.SEQUENTIAL, trackIds, selector);
    }
}
