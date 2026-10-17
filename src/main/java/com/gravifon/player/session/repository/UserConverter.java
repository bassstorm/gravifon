package com.gravifon.player.session.repository;

import com.gravifon.player.persistence.UserEntity;
import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import com.gravifon.player.session.model.Username;

final class UserConverter {
    private UserConverter() {
    }

    static User toDomain(UserEntity entity) {
        return new User(
                new UserId(entity.id()),
                new Username(entity.normalizedName(), entity.displayName()),
                entity.createdAt()
        );
    }

    static UserEntity toEntity(User user) {
        return new UserEntity(
                user.id().value(),
                user.username().normalizedName(),
                user.username().displayName(),
                user.createdAt()
        );
    }
}
