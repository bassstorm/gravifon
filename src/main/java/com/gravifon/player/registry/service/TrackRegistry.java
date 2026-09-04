package com.gravifon.player.registry.service;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.registry.metadata.TagWriteBackHandler;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.scan.LibraryScanCoordinator;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public Optional<Path> resolveTrackPath(String trackId) {
        return findTrackById(trackId)
                .filter(FileTrack.class::isInstance)
                .map(FileTrack.class::cast)
                .map(fileTrack ->
                        properties.getMusicRoot().resolve(fileTrack.relPath()).normalize());
    }

    @Transactional
    public Track updateStream(String trackId, String streamUrl, Instant expiresAfter) {
        Track track =
                findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
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
        Track track =
                findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
        TrackState state = new TrackState(true, new TrackError(errorKind, message, Instant.now(), "STREAM_REFRESH"));
        return trackRepository.save(track.withState(state));
    }

    @Transactional
    public Track updateMetadata(String trackId, Map<String, List<String>> metadata) {
        Track track =
                findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
        Map<String, List<String>> normalized = new LinkedHashMap<>();
        metadata.forEach((key, values) -> normalized.put(key, List.copyOf(values)));
        Track updated = track.withMetadata(normalized);
        writeBackHandlers.stream()
                .filter(handler -> handler.supports(updated))
                .findFirst()
                .ifPresent(handler -> handler.writeBack(updated, normalized));
        return trackRepository.save(updated);
    }

    @Transactional
    public Track reportState(String trackId, String kind, String message, boolean clear) {
        Track track =
                findTrackById(trackId).orElseThrow(() -> new IllegalArgumentException("Track not found: " + trackId));
        TrackState state = clear
                ? TrackState.healthy()
                : new TrackState(
                        true, new TrackError(kind == null ? "CLIENT_ERROR" : kind, message, Instant.now(), "CLIENT"));
        return trackRepository.save(track.withState(state));
    }
}
