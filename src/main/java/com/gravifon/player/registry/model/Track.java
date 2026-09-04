package com.gravifon.player.registry.model;

import java.util.List;
import java.util.Map;
import org.jmolecules.ddd.types.AggregateRoot;

public sealed interface Track extends AggregateRoot<Track, TrackId> permits FileTrack, StreamTrack {
    String id();

    default TrackId trackId() {
        return TrackId.of(id());
    }

    @Override
    default TrackId getId() {
        return trackId();
    }

    TrackKind kind();

    Map<String, List<String>> metadata();

    Long durationSeconds();

    TrackState state();

    Track withState(TrackState state);

    Track withMetadata(Map<String, List<String>> metadata);
}
