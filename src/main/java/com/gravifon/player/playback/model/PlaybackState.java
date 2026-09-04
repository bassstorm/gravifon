package com.gravifon.player.playback.model;

public record PlaybackState(
        String activePlaylistId,
        String currentTrackId,
        PlaybackMode playbackMode,
        TransportState transportState,
        long positionSeconds,
        String positionOrigin) {

    public PlaybackState(
            String activePlaylistId,
            String currentTrackId,
            PlaybackMode playbackMode,
            TransportState transportState,
            long positionSeconds) {
        this(activePlaylistId, currentTrackId, playbackMode, transportState, positionSeconds, "OBSERVED");
    }
}
