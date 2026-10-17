package com.gravifon.player.session.repository;

import com.gravifon.player.persistence.ClientSessionEntity;
import com.gravifon.player.persistence.UserEntity;
import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.UserId;

final class ClientSessionConverter {
    private ClientSessionConverter() {
    }

    static ClientSession toDomain(ClientSessionEntity entity) {
        return new ClientSession(
                new ClientSessionId(entity.id()),
                new UserId(entity.user().id()),
                entity.createdAt(),
                entity.lastActivityAt(),
                entity.invalidatedAt()
        );
    }

    static ClientSessionEntity toEntity(ClientSession session, UserEntity user) {
        return new ClientSessionEntity(
                session.id().value(),
                user,
                session.createdAt(),
                session.lastActivityAt(),
                session.invalidatedAt()
        );
    }
}
