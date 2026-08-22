package com.gravifon.player.api.model;

import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playback.model.PlaybackMode;
import java.util.List;

public record PlaylistResponse(
        String id,
        String name,
        List<String> trackIds,
        PlaybackMode mode,
        boolean active
) {
    public static PlaylistResponse from(Playlist playlist, String activePlaylistId) {
        return new PlaylistResponse(
                playlist.id(),
                playlist.name(),
                playlist.trackIds(),
                playlist.playbackMode(),
                playlist.id().equals(activePlaylistId)
        );
    }
}

