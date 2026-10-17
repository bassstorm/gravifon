package com.gravifon.player.session.model;

import java.util.Objects;

public record SessionInvalidated(ClientSessionId sessionId, UserId userId) {
    public SessionInvalidated {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(userId, "userId");
    }
}
