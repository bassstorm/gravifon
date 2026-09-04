package com.gravifon.player.api.model;

import com.gravifon.player.playback.model.PlaybackState;

public record PlaybackStateResponse(
        String activePlaylistId,
        String currentTrackId,
        String playbackMode,
        String transportState,
        long positionSeconds,
        String positionOrigin) {
    public static PlaybackStateResponse from(PlaybackState state) {
        return new PlaybackStateResponse(
                state.activePlaylistId(),
                state.currentTrackId(),
                state.playbackMode().name().toLowerCase(),
                state.transportState().name().toLowerCase(),
                state.positionSeconds(),
                state.positionOrigin().toLowerCase());
    }
}
