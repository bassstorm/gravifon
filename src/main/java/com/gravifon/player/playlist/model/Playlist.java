package com.gravifon.player.playlist.model;

import com.gravifon.player.playback.model.PlaybackMode;
import java.util.List;

public record Playlist(
        String id,
        String name,
                List<String> trackIds,
                PlaybackMode playbackMode
) {

        public Playlist(String id, String name, List<String> trackIds) {
                this(id, name, trackIds, PlaybackMode.SEQUENTIAL);
        }

        public Playlist {
                trackIds = List.copyOf(trackIds);
                playbackMode = playbackMode == null ? PlaybackMode.SEQUENTIAL : playbackMode;
        }
}

