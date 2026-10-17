package com.gravifon.player.session.repository;

import com.gravifon.player.persistence.AdmissionScope;
import com.gravifon.player.persistence.JpaAdmissionLockStore;
import com.gravifon.player.persistence.JpaUserStore;
import com.gravifon.player.persistence.UserEntity;
import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@InfrastructureRing
@Repository
@RequiredArgsConstructor
public class JpaUserRepository implements UserRepository {
    private final JpaUserStore store;
    private final JpaAdmissionLockStore admissionLocks;

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByNormalizedName(String normalizedName) {
        return store.findByNormalizedName(normalizedName).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return store.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        return toDomain(store.save(toEntity(user)));
    }

    @Override
    @Transactional
    public void lockCreation() {
        admissionLocks.lock(AdmissionScope.USER);
    }

    @Override
    @Transactional
    public Optional<User> lockById(UserId id) {
        return store.lockById(id.value()).map(this::toDomain);
    }

    private User toDomain(UserEntity entity) {
        return new User(new UserId(entity.id()), entity.normalizedName(), entity.displayName(), entity.createdAt());
    }

    private UserEntity toEntity(User user) {
        return new UserEntity(user.id().value(), user.normalizedName(), user.displayName(), user.createdAt());
    }
}
