package com.gravifon.player.session.model;

import java.time.Instant;
import java.util.Objects;

public record User(UserId id, Username username, Instant createdAt) {
    public User {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
