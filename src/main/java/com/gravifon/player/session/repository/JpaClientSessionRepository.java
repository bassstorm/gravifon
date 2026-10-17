package com.gravifon.player.session.repository;

import com.gravifon.player.persistence.ClientSessionEntity;
import com.gravifon.player.persistence.JpaClientSessionStore;
import com.gravifon.player.persistence.JpaUserStore;
import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@InfrastructureRing
@Repository
public class JpaClientSessionRepository implements ClientSessionRepository {
    private final JpaClientSessionStore store;
    private final JpaUserStore userStore;

    public JpaClientSessionRepository(JpaClientSessionStore store, JpaUserStore userStore) {
        this.store = store;
        this.userStore = userStore;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ClientSession> findById(ClientSessionId id) {
        return store.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional
    public ClientSession save(ClientSession session) {
        var user = userStore.getReferenceById(session.userId().value());
        return toDomain(store.save(
                new ClientSessionEntity(
                        session.id().value(),
                        user,
                        session.createdAt(),
                        session.lastActivityAt(),
                        session.invalidatedAt()
                )
        ));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientSession> findCleanupCandidates(Instant now, Duration inactivityTimeout) {
        if (now == null || inactivityTimeout == null || inactivityTimeout.isZero() || inactivityTimeout.isNegative()) {
            throw new IllegalArgumentException("Cleanup time and positive inactivity timeout are required");
        }
        return store.findCleanupCandidates(now.minus(inactivityTimeout)).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void deleteById(ClientSessionId id) {
        store.deleteById(id.value());
    }

    private ClientSession toDomain(ClientSessionEntity entity) {
        return new ClientSession(
                new ClientSessionId(entity.id()),
                new UserId(entity.user().id()),
                entity.createdAt(),
                entity.lastActivityAt(),
                entity.invalidatedAt()
        );
    }
}
