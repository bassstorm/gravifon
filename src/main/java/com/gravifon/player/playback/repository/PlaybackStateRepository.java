package com.gravifon.player.playback.repository;

import com.gravifon.player.playback.model.PlaybackState;
import java.util.Optional;

public interface PlaybackStateRepository {
    Optional<PlaybackState> find(String sessionId);
    void save(String sessionId, PlaybackState state, String positionOrigin);
}