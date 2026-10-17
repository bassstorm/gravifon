package com.gravifon.player.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAdmissionLockStore extends JpaRepository<AdmissionLockEntity, AdmissionScope> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select admission from AdmissionLockEntity admission where admission.scope = :scope")
    Optional<AdmissionLockEntity> lockByScope(@Param("scope") AdmissionScope scope);

    default void lock(AdmissionScope scope) {
        lockByScope(scope).orElseThrow(() -> new IllegalStateException(scope + " admission lock row is missing"));
    }
}
