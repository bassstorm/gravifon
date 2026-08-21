package com.gravifon.player.playlist.model;

import java.util.List;

public record Playlist(
        String id,
        String name,
        List<String> trackIds
) {
}

