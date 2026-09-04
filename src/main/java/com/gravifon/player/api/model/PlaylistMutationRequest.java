package com.gravifon.player.api.model;

import com.gravifon.player.playback.model.PlaybackMode;
import java.util.List;

public record PlaylistMutationRequest(
        String name,
        List<String> trackIds,
        List<String> sourceUrls,
        Boolean catalog,
        List<String> reorder,
        List<String> add,
        List<String> remove,
        PlaybackMode mode
) {
    public PlaylistMutationRequest {
        trackIds = trackIds == null ? List.of() : List.copyOf(trackIds);
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
        reorder = reorder == null ? List.of() : List.copyOf(reorder);
        add = add == null ? List.of() : List.copyOf(add);
        remove = remove == null ? List.of() : List.copyOf(remove);
    }
}
