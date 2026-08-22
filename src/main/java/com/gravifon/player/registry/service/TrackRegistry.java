package com.gravifon.player.registry.service;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.registry.identity.TrackIdentity;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.scan.LibraryScanner;
import com.gravifon.player.registry.scan.ScanReconciler;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;
import com.gravifon.player.registry.metadata.TagWriteBackHandler;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrackRegistry {

    private final GravifonProperties properties;
    private final LibraryScanner scanner;
    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final List<TagWriteBackHandler> writeBackHandlers;

    @Autowired
    public TrackRegistry(GravifonProperties properties, LibraryScanner scanner,
                         TrackRepository trackRepository, PlaylistRepository playlistRepository) {
        this(properties, scanner, trackRepository, playlistRepository, List.of());
    }

    public TrackRegistry(GravifonProperties properties, LibraryScanner scanner,
                         TrackRepository trackRepository, PlaylistRepository playlistRepository,
                         List<TagWriteBackHandler> writeBackHandlers) {
        this.properties = properties;
        this.scanner = scanner;
        this.trackRepository = trackRepository;
        this.playlistRepository = playlistRepository;
        this.writeBackHandlers = writeBackHandlers;
    }

    @PostConstruct
    public void scanOnStartup() {
        if (properties.isStartupScanEnabled()) {
            refresh();
        }
    }

    @Transactional
    public synchronized void refresh() {
        Path root = properties.getMusicRoot().toAbsolutePath().normalize();
        List<Track> existing = trackRepository.findAll();
        var referenced = playlistRepository.findAll().stream().flatMap(playlist -> playlist.trackIds().stream())
                .collect(Collectors.toSet());
        for (ScanReconciler.Decision decision : new ScanReconciler().reconcile(existing, scanner.scan(root), referenced)) {
            switch (decision.type()) {
                case CREATE, REFRESH -> trackRepository.save(trackFrom(decision));
                case TOMBSTONE -> trackRepository.findById(decision.trackId()).ifPresent(track ->
                        trackRepository.save(withState(track, new TrackState(true, new TrackError(
                                "SOURCE_MISSING", "File is missing: " + track.relPath(), Instant.now(), "SCAN")))));
                case REMOVE -> trackRepository.deleteById(decision.trackId());
            }
        }
    }

    public List<Track> listTracks() {
        return trackRepository.findAll();
    }

    public Optional<Track> findTrackById(String trackId) {
        return trackRepository.findById(trackId);
    }

    public Optional<Path> resolveTrackPath(String trackId) {
        return findTrackById(trackId).filter(track -> track.kind() == TrackKind.FILE)
                .map(track -> properties.getMusicRoot().resolve(track.relPath()).normalize());
    }

    @Transactional
    public Track updateStream(String trackId, String streamUrl, Instant expiresAfter) {
        Track track = findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
        return trackRepository.save(new Track(track.id(), track.kind(), track.metadata(), track.durationSeconds(),
                TrackState.healthy(), track.relPath(), track.format(), track.sourceUrl(), streamUrl, expiresAfter));
    }

    @Transactional
    public Track markStreamUnreachable(String trackId, String message) {
        return markStreamUnreachable(trackId, "STREAM_UNREACHABLE", message);
    }

    @Transactional
    public Track markStreamUnreachable(String trackId, String errorKind, String message) {
        Track track = findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
        TrackState state = new TrackState(true, new TrackError(errorKind, message, Instant.now(), "STREAM_REFRESH"));
        return trackRepository.save(withState(track, state));
    }

    @Transactional
    public Track updateMetadata(String trackId, Map<String, List<String>> metadata) {
        Track track = findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
        Map<String, List<String>> normalized = new java.util.LinkedHashMap<>();
        metadata.forEach((key, values) -> normalized.put(key, List.copyOf(values)));
        Track updated = withMetadata(track, normalized);
        writeBackHandlers.stream().filter(handler -> handler.supports(updated))
                .findFirst().ifPresent(handler -> handler.writeBack(updated, normalized));
        return trackRepository.save(updated);
    }

    @Transactional
    public Track reportState(String trackId, String kind, String message, boolean clear) {
        Track track = findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
        TrackState state = clear ? TrackState.healthy() : new TrackState(true,
                new TrackError(kind == null ? "CLIENT_ERROR" : kind, message, Instant.now(), "CLIENT"));
        return trackRepository.save(withState(track, state));
    }

        private Track trackFrom(ScanReconciler.Decision decision) {
        var file = decision.file();
        TrackState state = file.readable() ? TrackState.healthy() : new TrackState(true, new TrackError(
            "READ_ERROR", "Unable to read file: " + file.relativePath(), Instant.now(), "SCAN"));
        String id = decision.type() == ScanReconciler.DecisionType.CREATE
            ? TrackIdentity.forFile(Path.of(file.relativePath())) : decision.trackId();
        return new Track(id, TrackKind.FILE, file.metadata(), file.durationSeconds(), state,
            file.relativePath(), file.format(), null, null, null);
    }

        private Track withState(Track track, TrackState state) {
        return new Track(track.id(), track.kind(), track.metadata(), track.durationSeconds(), state,
            track.relPath(), track.format(), track.sourceUrl(), track.streamUrl(), track.expiresAfter());
    }

    private Track withMetadata(Track track, Map<String, List<String>> metadata) {
        return new Track(track.id(), track.kind(), metadata, track.durationSeconds(), track.state(),
                track.relPath(), track.format(), track.sourceUrl(), track.streamUrl(), track.expiresAfter());
    }
}