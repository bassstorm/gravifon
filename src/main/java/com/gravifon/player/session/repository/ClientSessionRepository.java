package com.gravifon.player.session.repository;

import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public interface ClientSessionRepository {
    Optional<ClientSession> findById(ClientSessionId id);

    ClientSession save(ClientSession session);

    List<ClientSession> findCleanupCandidates(Instant now, Duration inactivityTimeout);

    void deleteById(ClientSessionId id);
}
