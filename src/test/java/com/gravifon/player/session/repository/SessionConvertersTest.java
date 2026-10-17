package com.gravifon.player.session.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.persistence.ClientSessionEntity;
import com.gravifon.player.persistence.UserEntity;
import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SessionConvertersTest {
    @Test
    void userRoundTripPreservesIdentityAndCreationInstant() {
        User user = new User(new UserId("user-1"), "alice", "Alice", Instant.parse("2026-01-01T12:00:00Z"));

        UserEntity entity = UserConverter.toEntity(user);

        assertThat(UserConverter.toDomain(entity)).isEqualTo(user);
    }

    @Test
    void clientSessionRoundTripPreservesNullableInvalidationAndInstants() {
        Instant createdAt = Instant.parse("2026-01-01T12:00:00Z");
        Instant activityAt = createdAt.plusSeconds(90);
        UserEntity user = UserConverter.toEntity(new User(new UserId("user-2"), "bob", "Bob", createdAt));
        ClientSession activeSession =
                new ClientSession(
                        new ClientSessionId("session-active"),
                        new UserId("user-2"),
                        createdAt,
                        activityAt,
                        null
        );

        ClientSessionEntity activeEntity = ClientSessionConverter.toEntity(activeSession, user);
        ClientSession convertedActiveSession = ClientSessionConverter.toDomain(activeEntity);

        assertThat(activeEntity.user()).isSameAs(user);
        assertThat(activeEntity.invalidatedAt()).isNull();
        assertThat(convertedActiveSession).isEqualTo(activeSession);

        Instant invalidatedAt = activityAt.plusSeconds(30);
        ClientSession invalidatedSession = new ClientSession(
                new ClientSessionId("session-invalidated"),
                new UserId("user-2"),
                createdAt,
                activityAt,
                invalidatedAt
        );

        assertThat(ClientSessionConverter.toDomain(ClientSessionConverter.toEntity(invalidatedSession, user)))
            .isEqualTo(invalidatedSession);
    }
}
