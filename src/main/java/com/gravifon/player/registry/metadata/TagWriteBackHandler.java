package com.gravifon.player.registry.metadata;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackMetadata;

public interface TagWriteBackHandler {
    boolean supports(Track track);

    WriteBackResult writeBack(Track track, TrackMetadata metadata);

    record WriteBackResult(boolean accepted, String message) {}
}
