package com.gravifon.player.session.model;

import java.time.Instant;
import java.util.Objects;

public record User(UserId id, String normalizedName, String displayName, Instant createdAt) {
    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        Username username = new Username(normalizedName, displayName);
        normalizedName = username.normalizedName();
        displayName = username.displayName();
    }
}
