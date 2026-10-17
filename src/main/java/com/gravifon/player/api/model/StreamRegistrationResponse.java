package com.gravifon.player.api.model;

import com.gravifon.player.registry.model.Track;
import java.util.List;

public record StreamRegistrationResponse(List<String> trackIds) {
    public static StreamRegistrationResponse from(List<Track> tracks) {
        return new StreamRegistrationResponse(tracks.stream().map(Track::id).toList());
    }
}
