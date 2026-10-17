package com.gravifon.player.session.model;

import java.util.Objects;

public record Caller(ClientSessionId sessionId, UserId userId) {
    public Caller {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(userId, "userId");
    }
}
