package com.gravifon.player.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface JpaUserStore extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByNormalizedName(String normalizedName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserEntity> findLockedById(String id);
}
