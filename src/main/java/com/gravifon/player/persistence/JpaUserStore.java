package com.gravifon.player.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaUserStore extends JpaRepository<UserEntity, String> {
    Optional<UserEntity> findByNormalizedName(String normalizedName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where user.id = :id")
    Optional<UserEntity> lockById(@Param("id") String id);
}
