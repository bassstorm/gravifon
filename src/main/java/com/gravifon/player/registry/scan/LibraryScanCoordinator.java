package com.gravifon.player.registry.scan;

import com.gravifon.player.config.LibraryProperties;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class LibraryScanCoordinator {
    private final LibraryProperties properties;
    private final LibraryScanner scanner;
    private final TrackRepository trackRepository;
    private final List<ReferencedTrackIdsPort> referencedTrackIdsProviders;
    private final ScanReconciler scanReconciler;

    @Transactional
    public synchronized void scan() {
        Path root = properties.dir().toAbsolutePath().normalize();
        List<Track> existing = trackRepository.findAll();
        Set<String> referenced = referencedTrackIdsProviders
            .stream()
            .map(ReferencedTrackIdsPort::getReferencedTrackIds)
            .flatMap(Collection::stream)
            .collect(Collectors.toUnmodifiableSet());
        for (ScanReconciler.Decision decision : scanReconciler.reconcile(existing, scanner.scan(root), referenced)) {
            switch (decision.type()) {
                case CREATE, REFRESH -> trackRepository.save(trackFrom(decision));
                case TOMBSTONE -> trackRepository
                    .findById(decision.trackId())
                    .ifPresent(track -> {
                        String missingPath = track instanceof FileTrack fileTrack ? fileTrack.relPath().toString() : track.id();
                        trackRepository.save(track.withState(
                                new TrackState(true, TrackError.sourceMissing(missingPath, Instant.now()))
                        ));
                    });
                case REMOVE -> trackRepository.deleteById(decision.trackId());
            }
        }
    }

    private Track trackFrom(ScanReconciler.Decision decision) {
        var file = decision.file();
        TrackState state = file.readable()
                ? TrackState.healthy()
                : new TrackState(true, TrackError.readFailure(file.relativePath().toString(), Instant.now()));
        String id = decision.trackId();
        return new FileTrack(id, file.metadata(), file.durationSeconds(), state, file.relativePath(), file.format());
    }
}
