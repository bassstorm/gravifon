package com.gravifon.player.registry.model;

import java.util.Objects;
import org.jmolecules.ddd.types.Identifier;

public record TrackId(String value) implements Identifier {

    public TrackId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("TrackId value cannot be blank");
        }
    }

    public static TrackId of(String value) {
        return new TrackId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
