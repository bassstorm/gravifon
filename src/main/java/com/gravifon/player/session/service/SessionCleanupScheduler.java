package com.gravifon.player.session.service;

import com.gravifon.player.config.SessionProperties;
import com.gravifon.player.session.repository.ClientSessionRepository;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@ApplicationRing
@Slf4j
@Component
public class SessionCleanupScheduler {
    private final ClientSessionRepository sessions;
    private final SessionService sessionService;
    private final SessionProperties properties;
    private final Clock clock;

    public SessionCleanupScheduler(
            ClientSessionRepository sessions,
            SessionService sessionService,
            SessionProperties properties,
            Clock clock
    ) {
        this.sessions = sessions;
        this.sessionService = sessionService;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${gravifon.sessions.cleanup-cron}")
    public void cleanupExpiredSessions() {
        var candidates = sessions.findCleanupCandidates(clock.instant(), properties.inactivityTimeout());
        log.debug("Found {} client sessions eligible for cleanup", candidates.size());
        candidates.forEach(candidate -> sessionService.cleanupCandidate(candidate.id()));
    }
}
