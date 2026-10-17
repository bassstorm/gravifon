package com.gravifon.player.registry.service;

import com.gravifon.player.config.LibraryProperties;
import com.gravifon.player.error.EntityNotFoundException;
import com.gravifon.player.registry.metadata.TagWriteBackHandler;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.scan.LibraryScanCoordinator;
import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackRegistry {
    private final LibraryProperties properties;
    private final TrackRepository trackRepository;
    private final LibraryScanCoordinator scanCoordinator;
    private final List<TagWriteBackHandler> writeBackHandlers;

    public synchronized void refresh() {
        scanCoordinator.scan();
    }

    public List<Track> listTracks() {
        return trackRepository.findAll();
    }

    public Optional<Track> findTrackById(String trackId) {
        return trackRepository.findById(trackId);
    }

    public Track getTrack(String trackId) {
        return findTrackById(trackId).orElseThrow(() -> new EntityNotFoundException("Track not found: " + trackId));
    }

    public Path resolveTrackPath(FileTrack fileTrack) {
        return properties.dir().resolve(fileTrack.relPath()).normalize();
    }

    @Transactional
    public Track updateStream(String trackId, URI streamUrl, Instant expiresAfter) {
        Track track = getTrack(trackId);
        if (track instanceof StreamTrack streamTrack) {
            return trackRepository.save(streamTrack.withStream(streamUrl, expiresAfter));
        }
        return track;
    }

    @Transactional
    public Track markStreamUnreachable(String trackId, String message) {
        return markStreamUnreachable(trackId, "STREAM_UNREACHABLE", message);
    }

    @Transactional
    public Track markStreamUnreachable(String trackId, String errorKind, String message) {
        Track track = getTrack(trackId);
        TrackState state = new TrackState(true, TrackError.streamRefreshFailure(errorKind, message, Instant.now()));
        return trackRepository.save(track.withState(state));
    }

    @Transactional
    public Track updateMetadata(String trackId, TrackMetadata metadata) {
        Track track = getTrack(trackId);
        Track updated = track.withMetadata(metadata);
        writeBackHandlers
            .stream()
            .filter(handler -> handler.supports(updated))
            .findFirst()
            .ifPresent(handler -> handler.writeBack(updated, updated.metadata()));
        return trackRepository.save(updated);
    }

    @Transactional
    public Track reportFailure(String trackId, String kind, String message) {
        TrackError error = TrackError.clientFailure(kind, message, Instant.now());
        Track track = getTrack(trackId);
        return trackRepository.save(track.withState(new TrackState(true, error)));
    }

    @Transactional
    public Track clearError(String trackId) {
        Track track = getTrack(trackId);
        return trackRepository.save(track.withState(TrackState.healthy()));
    }
}
