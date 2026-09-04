package com.gravifon.player.registry.scan;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.registry.identity.TrackIdentity;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import jakarta.annotation.PostConstruct;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@ApplicationRing
@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryScanCoordinator {

    private final GravifonProperties properties;
    private final LibraryScanner scanner;
    private final TrackRepository trackRepository;
    private final ReferencedTrackIdsPort referencedTrackIdsPort;
    private final ScanReconciler scanReconciler;

    @PostConstruct
    public void scanOnStartup() {
        if (properties.isStartupScanEnabled()) {
            scan();
        }
    }

    @Transactional
    public synchronized void scan() {
        Path root = properties.getMusicRoot().toAbsolutePath().normalize();
        List<Track> existing = trackRepository.findAll();
        Set<String> referenced = referencedTrackIdsPort.getReferencedTrackIds();
        for (ScanReconciler.Decision decision : scanReconciler.reconcile(existing, scanner.scan(root), referenced)) {
            switch (decision.type()) {
                case CREATE, REFRESH -> trackRepository.save(trackFrom(decision));
                case TOMBSTONE -> trackRepository.findById(decision.trackId()).ifPresent(track -> {
                    String missingPath = track instanceof FileTrack fileTrack ? fileTrack.relPath() : track.id();
                    trackRepository.save(track.withState(new TrackState(true, new TrackError(
                            "SOURCE_MISSING", "File is missing: " + missingPath, Instant.now(), "SCAN"))));
                });
                case REMOVE -> trackRepository.deleteById(decision.trackId());
            }
        }
    }

    private Track trackFrom(ScanReconciler.Decision decision) {
        var file = decision.file();
        TrackState state = file.readable() ? TrackState.healthy() : new TrackState(true, new TrackError(
                "READ_ERROR", "Unable to read file: " + file.relativePath(), Instant.now(), "SCAN"));
        String id = decision.type() == ScanReconciler.DecisionType.CREATE
                ? TrackIdentity.forFile(Path.of(file.relativePath())) : decision.trackId();
        return new FileTrack(id, file.metadata(), file.durationSeconds(), state,
                file.relativePath(), file.format());
    }
}
