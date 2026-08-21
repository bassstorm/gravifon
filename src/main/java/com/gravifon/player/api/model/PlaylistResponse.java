package com.gravifon.player.api.model;

import com.gravifon.player.playlist.model.Playlist;
import java.util.List;

public record PlaylistResponse(
        String id,
        String name,
        List<String> trackIds,
        boolean active
) {
    public static PlaylistResponse from(Playlist playlist, String activePlaylistId) {
        return new PlaylistResponse(
                playlist.id(),
                playlist.name(),
                playlist.trackIds(),
                playlist.id().equals(activePlaylistId)
        );
    }
}

