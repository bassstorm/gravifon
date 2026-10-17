package com.gravifon.player.persistence;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaClientSessionStore extends JpaRepository<ClientSessionEntity, String> {
    @Query("""
            select session from ClientSessionEntity session
            where session.invalidatedAt is not null or session.lastActivityAt <= :cutoff
            """)
    List<ClientSessionEntity> findCleanupCandidates(@Param("cutoff") Instant cutoff);
}
