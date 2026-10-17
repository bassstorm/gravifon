package com.gravifon.player.api.model;

import com.gravifon.player.session.service.SessionAcquisition;
import java.time.Duration;
import java.time.Instant;

public record SessionResponse(
        String sessionId,
        String userId,
        String username,
        Instant lastActivityAt,
        Instant expiresAt
) {
    public static SessionResponse from(SessionAcquisition acquisition, Duration inactivityTimeout) {
        return new SessionResponse(
                acquisition.session().id().value(),
                acquisition.user().id().value(),
                acquisition.user().username().displayName(),
                acquisition.session().lastActivityAt(),
                acquisition.session().lastActivityAt().plus(inactivityTimeout)
        );
    }
}
