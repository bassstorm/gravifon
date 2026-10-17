package com.gravifon.player.session.repository;

import com.gravifon.player.persistence.AdmissionScope;
import com.gravifon.player.persistence.JpaAdmissionLockStore;
import com.gravifon.player.persistence.JpaUserStore;
import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class UserRepository {
    private final JpaUserStore store;
    private final JpaAdmissionLockStore admissionLocks;

    @Transactional(readOnly = true)
    public Optional<User> findByNormalizedName(String normalizedName) {
        return store.findByNormalizedName(normalizedName).map(UserConverter::toDomain);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return store.findById(id.value()).map(UserConverter::toDomain);
    }

    @Transactional
    public User save(User user) {
        return UserConverter.toDomain(store.save(UserConverter.toEntity(user)));
    }

    @Transactional
    public void lockCreation() {
        admissionLocks.lock(AdmissionScope.USER);
    }

    @Transactional
    public Optional<User> lockById(UserId id) {
        return store.lockById(id.value()).map(UserConverter::toDomain);
    }
}
