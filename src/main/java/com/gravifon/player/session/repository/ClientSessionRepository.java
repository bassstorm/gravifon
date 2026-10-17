package com.gravifon.player.session.repository;

import com.gravifon.player.persistence.JpaClientSessionStore;
import com.gravifon.player.persistence.JpaUserStore;
import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class ClientSessionRepository {
    private final JpaClientSessionStore store;
    private final JpaUserStore userStore;

    @Transactional(readOnly = true)
    public Optional<ClientSession> findById(ClientSessionId id) {
        return store.findById(id.value()).map(ClientSessionConverter::toDomain);
    }

    @Transactional
    public ClientSession save(ClientSession session) {
        var user = userStore.getReferenceById(session.userId().value());
        return ClientSessionConverter.toDomain(store.save(ClientSessionConverter.toEntity(session, user)));
    }

    @Transactional(readOnly = true)
    public List<ClientSession> findCleanupCandidates(Instant now, Duration inactivityTimeout) {
        return store
            .findByInvalidatedAtIsNotNullOrLastActivityAtLessThanEqual(now.minus(inactivityTimeout))
            .stream()
            .map(ClientSessionConverter::toDomain)
            .toList();
    }

    @Transactional
    public void deleteById(ClientSessionId id) {
        store.deleteById(id.value());
    }
}
