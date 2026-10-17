package com.gravifon.player.session.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ClientSessionTest {
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofHours(24);

    @Test
    void expiresAtExactInactivityBoundary() {
        ClientSession session = session(CREATED_AT, null);
        Instant expiresAt = CREATED_AT.plus(TIMEOUT);

        assertThat(session.isValidAt(expiresAt.minusNanos(1), TIMEOUT)).isTrue();
        assertThat(session.isValidAt(expiresAt, TIMEOUT)).isFalse();
    }

    @Test
    void renewsValidSessionWithAcceptedActivityTime() {
        ClientSession session = session(CREATED_AT, null);
        Instant activityAt = CREATED_AT.plusSeconds(30);

        ClientSession renewed = session.renewAt(activityAt, TIMEOUT);

        assertThat(renewed.lastActivityAt()).isEqualTo(activityAt);
        assertThat(renewed.createdAt()).isEqualTo(CREATED_AT);
        assertThat(renewed.isValidAt(activityAt, TIMEOUT)).isTrue();
    }

    @Test
    void invalidatedAndExpiredSessionsCannotBeRenewed() {
        ClientSession session = session(CREATED_AT, null);
        ClientSession invalidated = session.invalidateAt(CREATED_AT.plusSeconds(1));

        assertThat(invalidated.isValidAt(CREATED_AT.plusSeconds(2), TIMEOUT)).isFalse();
        assertThatThrownBy(() -> invalidated.renewAt(CREATED_AT.plusSeconds(2), TIMEOUT))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> session.renewAt(CREATED_AT.plus(TIMEOUT), TIMEOUT)).isInstanceOf(
                IllegalStateException.class
        );
    }

    @Test
    void rejectsNonPositiveInactivityTimeout() {
        ClientSession session = session(CREATED_AT, null);

        assertThatThrownBy(() -> session.isValidAt(CREATED_AT, Duration.ZERO)).isInstanceOf(
                IllegalArgumentException.class
        );
        assertThatThrownBy(() -> session.isValidAt(CREATED_AT, Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionIdDoesNotRenderItsToken() {
        assertThat(new ClientSessionId("opaque-token").toString()).isEqualTo("[redacted]");
    }

    private ClientSession session(Instant lastActivityAt, Instant invalidatedAt) {
        return new ClientSession(
                new ClientSessionId("opaque-token"),
                new UserId("user-id"),
                CREATED_AT,
                lastActivityAt,
                invalidatedAt
        );
    }
}
