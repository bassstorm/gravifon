package com.gravifon.player.registry.service;

import com.gravifon.player.config.GravifonProperties;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TrackRegistry {
    private final GravifonProperties properties;
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

    public Path resolveTrackPath(String trackId) {
        Track track = getTrack(trackId);
        if (track instanceof FileTrack fileTrack) {
            return properties.getMusicRoot().resolve(fileTrack.relPath()).normalize();
        }
        throw new EntityNotFoundException("Track path not found: " + trackId);
    }

    @Transactional
    public Track updateStream(String trackId, String streamUrl, Instant expiresAfter) {
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
        TrackState state = new TrackState(true, new TrackError(errorKind, message, Instant.now(), "STREAM_REFRESH"));
        return trackRepository.save(track.withState(state));
    }

    @Transactional
    public Track updateMetadata(String trackId, Map<String, List<String>> metadata) {
        Track track = getTrack(trackId);
        TrackMetadata normalized = new TrackMetadata(metadata);
        Track updated = track.withMetadata(normalized);
        writeBackHandlers
            .stream()
            .filter(handler -> handler.supports(updated))
            .findFirst()
            .ifPresent(handler -> handler.writeBack(updated, normalized.values()));
        return trackRepository.save(updated);
    }

    @Transactional
    public Track reportFailure(String trackId, String kind, String message) {
        if (!StringUtils.hasText(message) || kind != null && kind.isBlank()) {
            throw new IllegalArgumentException("A failure report requires a non-blank message and kind");
        }
        Track track = getTrack(trackId);
        TrackState state =
                new TrackState(
                        true,
                        new TrackError(kind == null ? "CLIENT_ERROR" : kind, message, Instant.now(), "CLIENT")
        );
        return trackRepository.save(track.withState(state));
    }

    @Transactional
    public Track clearError(String trackId) {
        Track track = getTrack(trackId);
        return trackRepository.save(track.withState(TrackState.healthy()));
    }
}
