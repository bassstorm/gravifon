package com.gravifon.player.session.service;

import com.gravifon.player.session.model.Caller;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.Username;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.stereotype.Service;

@ApplicationRing
@Service
@RequiredArgsConstructor
public class SessionService {
    private final SessionTransactions transactions;

    public SessionAcquisition acquire(Username username) {
        return transactions.acquire(username);
    }

    public Optional<Caller> validateAndRenew(ClientSessionId sessionId) {
        return transactions.validateAndRenew(sessionId);
    }

    public boolean expireIfNecessary(ClientSessionId sessionId) {
        return transactions.expireIfNecessary(sessionId);
    }

    public boolean cleanupCandidate(ClientSessionId sessionId) {
        return transactions.cleanupCandidate(sessionId);
    }

    public void invalidate(Caller caller) {
        transactions.invalidate(caller);
    }
}
