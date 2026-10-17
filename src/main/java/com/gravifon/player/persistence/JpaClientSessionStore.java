package com.gravifon.player.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaClientSessionStore extends JpaRepository<ClientSessionEntity, String> {
    List<ClientSessionEntity> findByInvalidatedAtIsNotNullOrLastActivityAtLessThanEqual(Instant cutoff);
}
