package com.gravifon.player.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    @Mock
    private UserRepository users;
    @Mock
    private ClientSessionRepository sessions;
    @Mock
    private SessionTransactions transactionWorker;
    @Mock
    private ApplicationEventPublisher events;

    @Test
    void transactionWorkerCreatesUserAndSessionAtFixedServerTime() {
        Username username = Username.from(" Alice ");
        AtomicReference<User> createdUser = new AtomicReference<>();
        when(users.findByNormalizedName("alice")).thenReturn(Optional.empty());
        when(users.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            createdUser.set(user);
            return user;
        });
        when(users.lockById(any(UserId.class))).thenAnswer(invocation -> Optional.of(createdUser.get()));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionTransactions worker = worker();

        SessionAcquisition acquisition = worker.acquire(username);

        assertThat(acquisition.user().normalizedName()).isEqualTo("alice");
        assertThat(acquisition.user().displayName()).isEqualTo("Alice");
        assertThat(acquisition.user().createdAt()).isEqualTo(NOW);
        assertThat(acquisition.session().createdAt()).isEqualTo(NOW);
        assertThat(acquisition.session().lastActivityAt()).isEqualTo(NOW);
        assertThat(acquisition.session().userId()).isEqualTo(acquisition.user().id());
    }

    @Test
    void transactionWorkerReusesUserAndCreatesDistinctSessions() {
        Username username = Username.from("alice");
        User existing = new User(new UserId("user-1"), "alice", "Alice", NOW.minusSeconds(10));
        when(users.findByNormalizedName("alice")).thenReturn(Optional.of(existing));
        when(users.lockById(existing.id())).thenReturn(Optional.of(existing));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionTransactions worker = worker();

        SessionAcquisition first = worker.acquire(username);
        SessionAcquisition second = worker.acquire(username);

        assertThat(first.user()).isEqualTo(existing);
        assertThat(second.user()).isEqualTo(existing);
        assertThat(first.session().id()).isNotEqualTo(second.session().id());
        assertThat(first.session().userId()).isEqualTo(second.session().userId());
    }

    @Test
    void transactionWorkerFailsIfResolvedUserCannotBeLocked() {
        Username username = Username.from("alice");
        User existing = new User(new UserId("user-1"), "alice", "Alice", NOW);
        when(users.findByNormalizedName("alice")).thenReturn(Optional.of(existing));
        when(users.lockById(existing.id())).thenReturn(Optional.empty());
        SessionTransactions worker = worker();

        assertThatThrownBy(() -> worker.acquire(username))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disappeared");
    }

    @Test
    void transactionWorkerTakesUserAdmissionLockBeforeResolvingUser() {
        Username username = Username.from("alice");
        User existing = new User(new UserId("user-1"), "alice", "Alice", NOW);
        when(users.findByNormalizedName("alice")).thenReturn(Optional.of(existing));
        when(users.lockById(existing.id())).thenReturn(Optional.of(existing));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionTransactions worker = worker();

        worker.acquire(username);

        InOrder order = inOrder(users);
        order.verify(users).lockCreation();
        order.verify(users).findByNormalizedName("alice");
        order.verify(users).lockById(existing.id());
    }

    @Test
    void facadePropagatesUnrelatedIntegrityFailureWithoutRetry() {
        Username username = Username.from("Alice");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("foreign key");
        when(transactionWorker.acquire(username)).thenThrow(failure);
        SessionService service = new SessionService(transactionWorker);

        assertThatThrownBy(() -> service.acquire(username)).isSameAs(failure);

        verify(transactionWorker).acquire(username);
    }

    @Test
    void facadePropagatesNormalizedNameConflictWithoutRetry() {
        Username username = Username.from("Alice");
        DataIntegrityViolationException conflict = new DataIntegrityViolationException("duplicate normalized name");
        when(transactionWorker.acquire(username)).thenThrow(conflict);
        SessionService service = new SessionService(transactionWorker);

        assertThatThrownBy(() -> service.acquire(username)).isSameAs(conflict);

        verify(transactionWorker).acquire(username);
    }

    @Test
    void validRequestLocksUserThenReReadsAndRenewsAtRequestInitiation() {
        User user = new User(new UserId("user-1"), "alice", "Alice", NOW.minusSeconds(120));
        ClientSession session = session("session-1", user.id(), NOW.minusSeconds(120), NOW.minusSeconds(60), null);
        when(sessions.findById(session.id())).thenReturn(Optional.of(session), Optional.of(session));
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionService service = new SessionService(worker());

        Optional<Caller> caller = service.validateAndRenew(session.id());

        assertThat(caller).contains(new Caller(session.id(), user.id()));
        InOrder order = inOrder(sessions, users);
        order.verify(sessions).findById(session.id());
        order.verify(users).lockById(user.id());
        order.verify(sessions).findById(session.id());
        order.verify(sessions).save(new ClientSession(session.id(), user.id(), session.createdAt(), NOW, null));
    }

    @Test
    void exactExpiryBoundaryCommitsInvalidationEventBeforeReturningInvalid() {
        User user = new User(new UserId("user-1"), "alice", "Alice", NOW.minus(Duration.ofDays(2)));
        ClientSession expired =
                session("session-1", user.id(), NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofHours(24)), null);
        when(sessions.findById(expired.id())).thenReturn(Optional.of(expired), Optional.of(expired));
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionService service = new SessionService(worker());

        assertThat(service.validateAndRenew(expired.id())).isEmpty();

        ClientSession invalidated = expired.invalidateAt(NOW);
        InOrder order = inOrder(sessions, events);
        order.verify(sessions).save(invalidated);
        order.verify(events).publishEvent(new SessionInvalidated(expired.id(), user.id()));
    }

    @Test
    void expireIfNecessaryRechecksUnderUserLockAndPublishesExpiration() {
        User user = new User(new UserId("user-1"), "alice", "Alice", NOW.minus(Duration.ofDays(2)));
        ClientSession expired =
                session("session-1", user.id(), NOW.minus(Duration.ofDays(2)), NOW.minus(Duration.ofHours(24)), null);
        when(sessions.findById(expired.id())).thenReturn(Optional.of(expired), Optional.of(expired));
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionService service = new SessionService(worker());

        assertThat(service.expireIfNecessary(expired.id())).isTrue();

        InOrder order = inOrder(sessions, users, events);
        order.verify(sessions).findById(expired.id());
        order.verify(users).lockById(user.id());
        order.verify(sessions).findById(expired.id());
        order.verify(sessions).save(expired.invalidateAt(NOW));
        order.verify(events).publishEvent(new SessionInvalidated(expired.id(), user.id()));
    }

    @Test
    void expireIfNecessaryKeepsRecentSessionValid() {
        User user = new User(new UserId("user-1"), "alice", "Alice", NOW.minusSeconds(120));
        ClientSession recent = session("session-1", user.id(), NOW.minusSeconds(120), NOW.minusSeconds(60), null);
        when(sessions.findById(recent.id())).thenReturn(Optional.of(recent), Optional.of(recent));
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        SessionService service = new SessionService(worker());

        assertThat(service.expireIfNecessary(recent.id())).isFalse();

        verify(sessions, times(0)).save(any(ClientSession.class));
        verifyNoInteractions(events);
    }

    @Test
    void invalidatedSessionCannotBeRenewedOrPublishAnotherInvalidation() {
        User user = new User(new UserId("user-1"), "alice", "Alice", NOW.minusSeconds(120));
        ClientSession invalidated =
                session("session-1", user.id(), NOW.minusSeconds(120), NOW.minusSeconds(60), NOW.minusSeconds(30));
        when(sessions.findById(invalidated.id())).thenReturn(Optional.of(invalidated), Optional.of(invalidated));
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        SessionService service = new SessionService(worker());

        assertThat(service.validateAndRenew(invalidated.id())).isEmpty();

        org.mockito.Mockito.verify(sessions, times(0)).save(any(ClientSession.class));
        verifyNoInteractions(events);
    }

    @Test
    void explicitInvalidationLocksUserAndPublishesTheLifecycleEvent() {
        User user = new User(new UserId("user-1"), "alice", "Alice", NOW.minusSeconds(120));
        ClientSession session = session("session-1", user.id(), NOW.minusSeconds(120), NOW.minusSeconds(60), null);
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        when(sessions.findById(session.id())).thenReturn(Optional.of(session));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionService service = new SessionService(worker());

        service.invalidate(new Caller(session.id(), user.id()));

        InOrder order = inOrder(users, sessions, events);
        order.verify(users).lockById(user.id());
        order.verify(sessions).findById(session.id());
        order.verify(sessions).save(session.invalidateAt(NOW));
        order.verify(events).publishEvent(new SessionInvalidated(session.id(), user.id()));
    }

    private SessionTransactions worker() {
        return new SessionTransactions(
                users,
                sessions,
                CLOCK,
                new SessionProperties(Duration.ofHours(24), "0 0 * * * *"),
                events
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
