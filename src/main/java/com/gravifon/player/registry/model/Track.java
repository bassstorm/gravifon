package com.gravifon.player.registry.model;

import java.util.List;
import java.util.Map;

public sealed interface Track permits FileTrack, StreamTrack {
    String id();

    TrackKind kind();

    Map<String, List<String>> metadata();

    Long durationSeconds();

    TrackState state();

    Track withState(TrackState state);

    Track withMetadata(Map<String, List<String>> metadata);
}
