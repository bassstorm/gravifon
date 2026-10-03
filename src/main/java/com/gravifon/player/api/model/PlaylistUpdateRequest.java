package com.gravifon.player.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.gravifon.player.playback.model.PlaybackMode;
import jakarta.validation.constraints.AssertTrue;
import java.util.List;

public record PlaylistUpdateRequest(
        String name,
        List<String> reorder,
        List<String> add,
        List<String> remove,
        PlaybackMode mode
) {
    public PlaylistUpdateRequest {
        reorder = reorder == null ? List.of() : List.copyOf(reorder);
        add = add == null ? List.of() : List.copyOf(add);
        remove = remove == null ? List.of() : List.copyOf(remove);
    }

    @AssertTrue(message = "must not be blank when provided")
    @JsonIgnore
    public boolean isNameValid() {
        return name == null || !name.isBlank();
    }
}
