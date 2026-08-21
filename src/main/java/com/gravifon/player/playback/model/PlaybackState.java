package com.gravifon.player.playback.model;

public record PlaybackState(
        String activePlaylistId,
        String currentTrackId,
        PlaybackMode playbackMode,
        TransportState transportState,
        long positionSeconds
) {
}

