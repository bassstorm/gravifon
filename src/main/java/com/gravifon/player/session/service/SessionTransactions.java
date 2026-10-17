package com.gravifon.player.session.service;

import com.gravifon.player.config.SessionProperties;
import com.gravifon.player.session.model.Caller;
import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.SessionInvalidated;
import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import com.gravifon.player.session.model.Username;
import com.gravifon.player.session.repository.ClientSessionRepository;
import com.gravifon.player.session.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionTransactions {
    private final UserRepository users;
    private final ClientSessionRepository sessions;
    private final Clock clock;
    private final SessionProperties properties;
    private final ApplicationEventPublisher events;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SessionAcquisition acquire(Username username) {
        users.lockCreation();
        Instant now = clock.instant();
        User user = users
            .findByNormalizedName(username.normalizedName())
            .orElseGet(() -> users.save(
                    new User(
                            new UserId(UUID.randomUUID().toString()),
                            username.normalizedName(),
                            username.displayName(),
                            now
                    )
            ));
        User lockedUser = users
            .lockById(user.id())
            .orElseThrow(() -> new IllegalStateException("Resolved user disappeared before session acquisition"));
        ClientSession session =
                new ClientSession(new ClientSessionId(UUID.randomUUID().toString()), lockedUser.id(), now, now, null);
        return new SessionAcquisition(lockedUser, sessions.save(session));
    }

    @Transactional
    public Optional<Caller> validateAndRenew(ClientSessionId id) {
        Instant now = clock.instant();
        ClientSession candidate = sessions.findById(id).orElse(null);
        if (candidate == null || users.lockById(candidate.userId()).isEmpty()) {
            return java.util.Optional.empty();
        }
        ClientSession current = sessions.findById(id).orElse(null);
        if (current == null || current.invalidatedAt() != null) {
            return java.util.Optional.empty();
        }
        if (!current.isValidAt(now, properties.inactivityTimeout())) {
            invalidateExpired(current, now);
            return java.util.Optional.empty();
        }
        ClientSession renewed = current.renewAt(now, properties.inactivityTimeout());
        sessions.save(renewed);
        return java.util.Optional.of(new Caller(renewed.id(), renewed.userId()));
    }

    @Transactional
    public boolean expireIfNecessary(ClientSessionId id) {
        Instant now = clock.instant();
        ClientSession candidate = sessions.findById(id).orElse(null);
        if (candidate == null || users.lockById(candidate.userId()).isEmpty()) {
            return false;
        }
        ClientSession current = sessions.findById(id).orElse(null);
        if (current == null
                || current.invalidatedAt() != null
                || current.isValidAt(now, properties.inactivityTimeout())) {
            return false;
        }
        invalidateExpired(current, now);
        return true;
    }

    @Transactional
    public boolean cleanupCandidate(ClientSessionId id) {
        ClientSession candidate = sessions.findById(id).orElse(null);
        if (candidate == null || users.lockById(candidate.userId()).isEmpty()) {
            return false;
        }
        ClientSession current = sessions.findById(id).orElse(null);
        if (current == null) {
            return false;
        }
        if (!current.userId().equals(candidate.userId())) {
            throw new IllegalStateException("Session owner changed while acquiring its user lock");
        }
        if (current.invalidatedAt() == null) {
            Instant now = clock.instant();
            if (current.isValidAt(now, properties.inactivityTimeout())) {
                return false;
            }
            invalidateExpired(current, now);
        }
        sessions.deleteById(id);
        return true;
    }

    @Transactional
    public void invalidate(Caller caller) {
        users
            .lockById(caller.userId())
            .orElseThrow(() -> new IllegalStateException("Caller user no longer exists"));
        ClientSession current = sessions
            .findById(caller.sessionId())
            .orElseThrow(() -> new IllegalStateException("Caller session no longer exists"));
        if (!current.userId().equals(caller.userId())) {
            throw new IllegalStateException("Caller session does not belong to the supplied user");
        }
        if (current.invalidatedAt() == null) {
            ClientSession invalidated = current.invalidateAt(clock.instant());
            sessions.save(invalidated);
            events.publishEvent(new SessionInvalidated(invalidated.id(), invalidated.userId()));
        }
    }

    private void invalidateExpired(ClientSession session, Instant now) {
        ClientSession invalidated = session.invalidateAt(now);
        sessions.save(invalidated);
        events.publishEvent(new SessionInvalidated(invalidated.id(), invalidated.userId()));
    }
}
