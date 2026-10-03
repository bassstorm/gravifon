package com.gravifon.player.playlist.service;

import com.gravifon.player.playback.model.PlaybackMode;
import java.util.List;

public record PlaylistMutation(
        String name,
        List<String> trackIds,
        List<String> sourceUrls,
        Boolean catalog,
        List<String> reorder,
        List<String> add,
        List<String> remove,
        PlaybackMode mode
) {
    public PlaylistMutation {
        trackIds = trackIds == null ? List.of() : List.copyOf(trackIds);
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
        reorder = reorder == null ? List.of() : List.copyOf(reorder);
        add = add == null ? List.of() : List.copyOf(add);
        remove = remove == null ? List.of() : List.copyOf(remove);
    }
}
