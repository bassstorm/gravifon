package com.gravifon.player.session.model;

import java.util.Objects;
import org.jmolecules.ddd.types.Identifier;

public record ClientSessionId(String value) implements Identifier {
    public ClientSessionId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ClientSessionId value cannot be blank");
        }
    }

    public static ClientSessionId of(String value) {
        return new ClientSessionId(value);
    }

    @Override
    public String toString() {
        return "[redacted]";
    }
}
