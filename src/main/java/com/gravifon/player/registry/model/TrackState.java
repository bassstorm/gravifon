package com.gravifon.player.registry.model;

public record TrackState(boolean failing, TrackError lastError) {
    public static TrackState healthy() {
        return new TrackState(false, null);
    }
}
