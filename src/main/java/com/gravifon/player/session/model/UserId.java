package com.gravifon.player.session.model;

import java.util.Objects;
import org.jmolecules.ddd.types.Identifier;

public record UserId(String value) implements Identifier {
    public UserId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("UserId value cannot be blank");
        }
    }

    public static UserId of(String value) {
        return new UserId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
