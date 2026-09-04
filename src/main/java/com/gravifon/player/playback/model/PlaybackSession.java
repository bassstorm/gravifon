package com.gravifon.player.playback.model;

import java.util.List;
import org.jmolecules.ddd.types.AggregateRoot;
import org.jmolecules.ddd.types.Identifier;

public record PlaybackSession(
        SessionId id,
        String activePlaylistId,
        String currentTrackId,
        PlaybackMode playbackMode,
        TransportState transportState,
        long observedPositionSeconds,
        Long reportedPositionSeconds)
        implements AggregateRoot<PlaybackSession, PlaybackSession.SessionId> {

    public record SessionId(String value) implements Identifier {
        public static SessionId of(String value) {
            return new SessionId(value);
        }
    }

    public static PlaybackSession initial(String sessionId) {
        return new PlaybackSession(
                SessionId.of(sessionId), null, null, PlaybackMode.SEQUENTIAL, TransportState.STOPPED, 0L, null);
    }

    public static PlaybackSession fromState(String sessionId, PlaybackState state) {
        if (state == null) {
            return initial(sessionId);
        }
        boolean reported = "REPORTED".equals(state.positionOrigin());
        return new PlaybackSession(
                SessionId.of(sessionId),
                state.activePlaylistId(),
                state.currentTrackId(),
                state.playbackMode(),
                state.transportState() == TransportState.PLAYING ? TransportState.PAUSED : state.transportState(),
                state.positionSeconds(),
                reported ? state.positionSeconds() : null);
    }

    @Override
    public SessionId getId() {
        return id;
    }

    public PlaybackState toPlaybackState() {
        boolean reported = reportedPositionSeconds != null;
        return new PlaybackState(
                activePlaylistId,
                currentTrackId,
                playbackMode,
                transportState,
                reported ? reportedPositionSeconds : observedPositionSeconds,
                reported ? "REPORTED" : "OBSERVED");
    }

    public PlaybackSession selectPlaylist(
            String newPlaylistId, PlaybackMode mode, List<String> activeTrackIds, TrackSelector selector) {
        String initialTrack = selector.selectInitialTrack(activeTrackIds).orElse(null);
        return new PlaybackSession(id, newPlaylistId, initialTrack, mode, transportState, 0L, null);
    }

    public PlaybackSession selectTrack(String trackId, List<String> activeTrackIds) {
        if (trackId == null || !activeTrackIds.contains(trackId)) {
            throw new IllegalArgumentException("Track is not in the active playlist: " + trackId);
        }
        return new PlaybackSession(id, activePlaylistId, trackId, playbackMode, transportState, 0L, null);
    }

    public PlaybackSession setMode(PlaybackMode newMode) {
        if (newMode == null) {
            throw new IllegalArgumentException("Playback mode is required");
        }
        return new PlaybackSession(
                id,
                activePlaylistId,
                currentTrackId,
                newMode,
                transportState,
                observedPositionSeconds,
                reportedPositionSeconds);
    }

    public PlaybackSession setTransportState(
            TransportState newTransport, List<String> activeTrackIds, TrackSelector selector) {
        if (newTransport == null) {
            throw new IllegalArgumentException("Transport state is required");
        }
        String trackId = currentTrackId;
        long observed = observedPositionSeconds;
        Long reported = reportedPositionSeconds;
        if (newTransport == TransportState.PLAYING && trackId == null) {
            trackId = selector.selectInitialTrack(activeTrackIds).orElse(null);
        }
        if (newTransport == TransportState.STOPPED) {
            observed = 0L;
            reported = null;
        }
        return new PlaybackSession(id, activePlaylistId, trackId, playbackMode, newTransport, observed, reported);
    }

    public PlaybackSession nextTrack(List<String> activeTrackIds, TrackSelector selector) {
        if (activeTrackIds.isEmpty()) {
            return new PlaybackSession(id, activePlaylistId, null, playbackMode, transportState, 0L, null);
        }
        String nextTrack =
                selector.selectNextTrack(activeTrackIds, currentTrackId).orElse(null);
        return new PlaybackSession(id, activePlaylistId, nextTrack, playbackMode, transportState, 0L, null);
    }

    public PlaybackSession reportPosition(String trackId, long positionSeconds, Long currentTrackDuration) {
        if (positionSeconds < 0) {
            throw new IllegalArgumentException("Position must be >= 0 seconds");
        }
        if (trackId == null
                || !trackId.equals(currentTrackId)
                || positionSeconds > observedPositionSeconds
                || (currentTrackDuration != null && positionSeconds > currentTrackDuration)) {
            return this;
        }
        return new PlaybackSession(
                id,
                activePlaylistId,
                currentTrackId,
                playbackMode,
                transportState,
                observedPositionSeconds,
                positionSeconds);
    }

    public PlaybackSession observeStream(String trackId, long endByte, long totalBytes, Long durationSeconds) {
        if (trackId == null
                || !trackId.equals(currentTrackId)
                || totalBytes <= 0
                || endByte < 0
                || durationSeconds == null) {
            return this;
        }
        long streamedPosition =
                Math.min(durationSeconds, (long) (((double) endByte + 1d) / totalBytes * durationSeconds));
        long newObserved = Math.max(observedPositionSeconds, streamedPosition);
        return new PlaybackSession(
                id,
                activePlaylistId,
                currentTrackId,
                playbackMode,
                transportState,
                newObserved,
                reportedPositionSeconds);
    }

    public PlaybackSession initializeClient() {
        if (transportState == TransportState.PLAYING) {
            return new PlaybackSession(
                    id,
                    activePlaylistId,
                    currentTrackId,
                    playbackMode,
                    TransportState.PAUSED,
                    observedPositionSeconds,
                    reportedPositionSeconds);
        }
        return this;
    }
}
