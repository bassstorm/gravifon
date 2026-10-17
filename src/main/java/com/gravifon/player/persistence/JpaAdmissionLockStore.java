package com.gravifon.player.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface JpaAdmissionLockStore extends JpaRepository<AdmissionLockEntity, AdmissionScope> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AdmissionLockEntity> findLockedByScope(AdmissionScope scope);

    default void lock(AdmissionScope scope) {
        findLockedByScope(scope).orElseThrow(() -> new IllegalStateException(scope + " admission lock row is missing"));
    }
}
