package com.gravifon.player.session.service;

import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.User;
import java.util.Objects;

public record SessionAcquisition(User user, ClientSession session) {
    public SessionAcquisition {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(session, "session");
        if (!user.id().equals(session.userId())) {
            throw new IllegalArgumentException("Session user must match the acquisition user");
        }
    }
}
