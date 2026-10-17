package com.gravifon.player.registry.model;

public sealed interface Track permits FileTrack, StreamTrack {
    String id();

    TrackKind kind();

    TrackMetadata metadata();

    Long durationSeconds();

    TrackState state();

    Track withState(TrackState state);

    Track withMetadata(TrackMetadata metadata);
}
