package com.gravifon.player.session.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record ClientSession(
        ClientSessionId id,
        UserId userId,
        Instant createdAt,
        Instant lastActivityAt,
        Instant invalidatedAt
) {
    public ClientSession {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt");
        if (lastActivityAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Session activity cannot precede creation");
        }
        if (invalidatedAt != null && invalidatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("Session invalidation cannot precede creation");
        }
    }

    public boolean isValidAt(Instant now, Duration inactivityTimeout) {
        Objects.requireNonNull(now, "now");
        requirePositive(inactivityTimeout);
        return invalidatedAt == null && now.isBefore(lastActivityAt.plus(inactivityTimeout));
    }

    public ClientSession renewAt(Instant now, Duration inactivityTimeout) {
        if (!isValidAt(now, inactivityTimeout)) {
            throw new IllegalStateException("An expired or invalidated session cannot be renewed");
        }
        if (now.isBefore(lastActivityAt)) {
            throw new IllegalArgumentException("Session activity cannot move backwards");
        }
        return new ClientSession(id, userId, createdAt, now, invalidatedAt);
    }

    public ClientSession invalidateAt(Instant at) {
        Objects.requireNonNull(at, "at");
        if (invalidatedAt != null) {
            return this;
        }
        if (at.isBefore(createdAt)) {
            throw new IllegalArgumentException("Session invalidation cannot precede creation");
        }
        return new ClientSession(id, userId, createdAt, lastActivityAt, at);
    }

    private static void requirePositive(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Session inactivity timeout must be positive");
        }
    }
}
