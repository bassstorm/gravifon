package com.gravifon.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gravifon.player.config.SessionProperties;
import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import com.gravifon.player.session.model.Username;
import com.gravifon.player.session.repository.ClientSessionRepository;
import com.gravifon.player.session.repository.JpaClientSessionRepository;
import com.gravifon.player.session.repository.JpaUserRepository;
import com.gravifon.player.session.repository.UserRepository;
import com.gravifon.player.session.service.SessionAcquisition;
import com.gravifon.player.session.service.SessionTransactions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
@Import({JpaUserRepository.class, JpaClientSessionRepository.class})
class SessionRepositoryDataJpaTest {
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    @Autowired
    private UserRepository users;
    @Autowired
    private ClientSessionRepository sessions;

    @Test
    void userAndSessionRoundTripRetainsAssociationAndLifecycleTimes() {
        User user = new User(new UserId("user-1"), "alice", "Alice", CREATED_AT);
        users.save(user);
        ClientSession session = session("session-1", user.id(), CREATED_AT, CREATED_AT.plusSeconds(30), null);

        sessions.save(session);

        assertThat(users.findByNormalizedName("alice")).contains(user);
        assertThat(sessions.findById(session.id())).contains(session);
    }

    @Test
    void normalizedUsernameHasDatabaseUniquenessConstraint() {
        users.save(new User(new UserId("user-1"), "alice", "Alice", CREATED_AT));
        users.save(new User(new UserId("user-2"), "alice", "alice", CREATED_AT.plusSeconds(1)));

        assertThatThrownBy(() -> users.findByNormalizedName("alice")).isInstanceOf(
                DataIntegrityViolationException.class
        );
    }

    @Test
    void cleanupCandidatesIncludeExpiredAndInvalidatedSessionsOnly() {
        User user = new User(new UserId("user-1"), "alice", "Alice", CREATED_AT);
        users.save(user);
        Instant now = CREATED_AT.plus(Duration.ofDays(2));
        Duration timeout = Duration.ofHours(24);
        ClientSession recent = session("recent", user.id(), CREATED_AT, now.minusSeconds(60), null);
        ClientSession expired = session("expired", user.id(), CREATED_AT, now.minus(timeout), null);
        ClientSession invalidated =
                session("invalidated", user.id(), CREATED_AT, now.minusSeconds(60), now.minusSeconds(30));
        sessions.save(recent);
        sessions.save(expired);
        sessions.save(invalidated);

        List<ClientSession> candidates = sessions.findCleanupCandidates(now, timeout);

        assertThat(candidates).extracting(ClientSession::id).containsExactlyInAnyOrder(expired.id(), invalidated.id());
    }

    @Test
    void deletingSessionPreservesItsUser() {
        User user = new User(new UserId("user-1"), "alice", "Alice", CREATED_AT);
        users.save(user);
        ClientSession session = session("session-1", user.id(), CREATED_AT, CREATED_AT, null);
        sessions.save(session);

        sessions.deleteById(session.id());

        assertThat(sessions.findById(session.id())).isEmpty();
        assertThat(users.findById(user.id())).contains(user);
    }

    @Test
    void acquisitionReusesNormalizedUserWithFirstDisplaySpellingAndDistinctSessions() {
        SessionTransactions transactions = new SessionTransactions(users, sessions, Clock.fixed(
                CREATED_AT,
                ZoneOffset.UTC
        ), new SessionProperties(Duration.ofHours(24), "-"), event -> {});

        SessionAcquisition first = transactions.acquire(Username.from(" Alice "));
        SessionAcquisition second = transactions.acquire(Username.from("alice"));

        assertThat(second.user().id()).isEqualTo(first.user().id());
        assertThat(first.user().displayName()).isEqualTo("Alice");
        assertThat(second.user().displayName()).isEqualTo("Alice");
        assertThat(second.session().id()).isNotEqualTo(first.session().id());
        assertThat(first.session().userId()).isEqualTo(second.session().userId());
    }

    @Test
    void lockedUserCanBeLoadedAndUpdatedThroughTheRepository() {
        User user = new User(new UserId("user-1"), "alice", "Alice", CREATED_AT);
        users.save(user);
        ClientSession session = session("session-1", user.id(), CREATED_AT, CREATED_AT, null);
        sessions.save(session);

        users.lockById(user.id()).orElseThrow();
        Instant activityAt = CREATED_AT.plusSeconds(60);
        sessions.save(session.renewAt(activityAt, Duration.ofHours(24)));

        assertThat(sessions.findById(session.id())).get().extracting(ClientSession::lastActivityAt).isEqualTo(
                activityAt
        );
    }

    private ClientSession session(
            String id,
            UserId userId,
            Instant createdAt,
            Instant lastActivityAt,
            Instant invalidatedAt
    ) {
        return new ClientSession(new ClientSessionId(id), userId, createdAt, lastActivityAt, invalidatedAt);
    }
}
