package com.gravifon.player.api.model;

import com.gravifon.player.playback.model.PlaybackMode;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record PlaylistCreateRequest(
        @NotBlank String name,
        List<String> trackIds,
        List<String> sourceUrls,
        Boolean catalog,
        PlaybackMode mode
) {
    public PlaylistCreateRequest {
        trackIds = trackIds == null ? List.of() : List.copyOf(trackIds);
        sourceUrls = sourceUrls == null ? List.of() : List.copyOf(sourceUrls);
    }
}
