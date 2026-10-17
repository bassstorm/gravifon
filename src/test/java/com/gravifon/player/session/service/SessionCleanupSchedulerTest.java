package com.gravifon.player.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.SessionProperties;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SessionCleanupSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-01-02T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofHours(24);
    private static final SessionProperties PROPERTIES = new SessionProperties(TIMEOUT, "0 0 * * * *");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    @Mock
    private ClientSessionRepository sessions;
    @Mock
    private SessionService sessionService;
    @Mock
    private UserRepository users;
    @Mock
    private ApplicationEventPublisher events;

    @Test
    void scheduledJobSynchronouslyDelegatesEachCandidate() {
        ClientSession first = session("session-1", "user-1", NOW.minus(TIMEOUT));
        ClientSession second = session("session-2", "user-2", NOW.minus(TIMEOUT));
        when(sessions.findCleanupCandidates(NOW, TIMEOUT)).thenReturn(List.of(first, second));
        SessionCleanupScheduler scheduler = new SessionCleanupScheduler(sessions, sessionService, PROPERTIES, CLOCK);

        scheduler.cleanupExpiredSessions();

        verify(sessionService).cleanupCandidate(first.id());
        verify(sessionService).cleanupCandidate(second.id());
    }

    @Test
    void scheduledJobPropagatesCleanupFailureAndDoesNotHideIt() {
        ClientSession first = session("session-1", "user-1", NOW.minus(TIMEOUT));
        ClientSession second = session("session-2", "user-2", NOW.minus(TIMEOUT));
        IllegalStateException failure = new IllegalStateException("cleanup failed");
        when(sessions.findCleanupCandidates(NOW, TIMEOUT)).thenReturn(List.of(first, second));
        when(sessionService.cleanupCandidate(first.id())).thenThrow(failure);
        SessionCleanupScheduler scheduler = new SessionCleanupScheduler(sessions, sessionService, PROPERTIES, CLOCK);

        assertThatThrownBy(scheduler::cleanupExpiredSessions).isSameAs(failure);

        verify(sessionService).cleanupCandidate(first.id());
        verify(sessionService, never()).cleanupCandidate(second.id());
    }

    @Test
    void cleanupWorkerRechecksCandidateAfterWaitingForUserLock() {
        User user = new User(new UserId("user-1"), Username.from("Alice"), NOW.minus(Duration.ofDays(2)));
        ClientSession candidate = session("session-1", user.id().value(), NOW.minus(TIMEOUT));
        ClientSession renewed = session("session-1", user.id().value(), NOW.minusSeconds(10));
        when(sessions.findById(candidate.id())).thenReturn(Optional.of(candidate), Optional.of(renewed));
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        SessionTransactions transactions = new SessionTransactions(users, sessions, CLOCK, PROPERTIES, events);

        assertThat(transactions.cleanupCandidate(candidate.id())).isFalse();

        verify(sessions, never()).deleteById(any(ClientSessionId.class));
        verifyNoInteractions(events);
    }

    @Test
    void cleanupWorkerPublishesExpirationBeforeDeletingOnlyTheSession() {
        User user = new User(new UserId("user-1"), Username.from("Alice"), NOW.minus(Duration.ofDays(2)));
        ClientSession expired = session("session-1", user.id().value(), NOW.minus(TIMEOUT));
        when(sessions.findById(expired.id())).thenReturn(Optional.of(expired), Optional.of(expired));
        when(users.lockById(user.id())).thenReturn(Optional.of(user));
        when(sessions.save(any(ClientSession.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SessionTransactions transactions = new SessionTransactions(users, sessions, CLOCK, PROPERTIES, events);

        assertThat(transactions.cleanupCandidate(expired.id())).isTrue();

        InOrder order = inOrder(sessions, users, events);
        order.verify(sessions).findById(expired.id());
        order.verify(users).lockById(user.id());
        order.verify(sessions).findById(expired.id());
        order.verify(sessions).save(expired.invalidateAt(NOW));
        order.verify(events).publishEvent(new SessionInvalidated(expired.id(), user.id()));
        order.verify(sessions).deleteById(expired.id());
    }

    private static ClientSession session(String id, String userId, Instant lastActivityAt) {
        return new ClientSession(
                new ClientSessionId(id),
                new UserId(userId),
                lastActivityAt.minusSeconds(60),
                lastActivityAt,
                null
        );
    }
}
