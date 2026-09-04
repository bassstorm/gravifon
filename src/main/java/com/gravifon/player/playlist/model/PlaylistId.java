package com.gravifon.player.playlist.model;

import java.util.Objects;
import org.jmolecules.ddd.types.Identifier;

public record PlaylistId(String value) implements Identifier {

    public PlaylistId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("PlaylistId value cannot be blank");
        }
    }

    public static PlaylistId of(String value) {
        return new PlaylistId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
