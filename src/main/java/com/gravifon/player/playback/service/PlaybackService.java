package com.gravifon.player.playback.service;

import com.gravifon.player.catalog.model.Track;
import com.gravifon.player.catalog.service.MediaCatalogService;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.service.InMemoryPlaylistService;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlaybackService {

    private final InMemoryPlaylistService playlistService;
    private final MediaCatalogService catalogService;
    private final Random random;

    private String currentTrackId;
    private PlaybackMode playbackMode = PlaybackMode.SEQUENTIAL;
    private TransportState transportState = TransportState.STOPPED;
    private long observedPositionSeconds;
    private Long reportedPositionSeconds;

    @Autowired
    public PlaybackService(InMemoryPlaylistService playlistService, MediaCatalogService catalogService) {
        this(playlistService, catalogService, new Random());
    }

    PlaybackService(InMemoryPlaylistService playlistService, MediaCatalogService catalogService, Random random) {
        this.playlistService = playlistService;
        this.catalogService = catalogService;
        this.random = random;
    }

    public synchronized PlaybackState getState() {
        return snapshot();
    }

    public synchronized PlaybackState selectPlaylist(String playlistId) {
        playlistService.selectPlaylist(playlistId);
        selectInitialTrack();
        return snapshot();
    }

    public synchronized PlaybackState selectTrack(String trackId) {
        if (trackId == null || !activeTrackIds().contains(trackId)) {
            throw new IllegalArgumentException("Track is not in the active playlist: " + trackId);
        }
        replaceCurrentTrack(trackId);
        return snapshot();
    }

    public synchronized PlaybackState setMode(PlaybackMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Playback mode is required");
        }
        this.playbackMode = mode;
        return snapshot();
    }

    public synchronized PlaybackState setTransportState(TransportState transportState) {
        if (transportState == null) {
            throw new IllegalArgumentException("Transport state is required");
        }
        if (transportState == TransportState.PLAYING && currentTrackId == null) {
            selectInitialTrack();
        }
        this.transportState = transportState;
        if (transportState == TransportState.STOPPED) {
            clearPosition();
        }
        return snapshot();
    }

    public synchronized PlaybackState nextTrack() {
        List<String> tracks = activeTrackIds();
        if (tracks.isEmpty()) {
            currentTrackId = null;
            clearPosition();
            return snapshot();
        }

        replaceCurrentTrack(switch (playbackMode) {
            case SEQUENTIAL -> nextSequential(tracks);
            case RANDOM -> tracks.get(random.nextInt(tracks.size()));
        });
        return snapshot();
    }

    public synchronized PlaybackState reportPosition(String trackId, long positionSeconds) {
        if (positionSeconds < 0) {
            throw new IllegalArgumentException("Position must be >= 0 seconds");
        }
        if (trackId == null || !trackId.equals(currentTrackId) || positionSeconds > observedPositionSeconds
                || currentTrackDurationSeconds().map(duration -> positionSeconds > duration).orElse(false)) {
            return snapshot();
        }
        reportedPositionSeconds = positionSeconds;
        return snapshot();
    }

    public synchronized void observeStream(String trackId, long endByte, long totalBytes) {
        if (trackId == null || !trackId.equals(currentTrackId) || totalBytes <= 0 || endByte < 0) {
            return;
        }
        currentTrackDurationSeconds().ifPresent(duration -> {
                long streamedPosition = Math.min(duration,
                    (long) (((double) endByte + 1d) / totalBytes * duration));
            observedPositionSeconds = Math.max(observedPositionSeconds, streamedPosition);
        });
    }

    private void selectInitialTrack() {
        List<String> tracks = activeTrackIds();
        if (tracks.isEmpty()) {
            currentTrackId = null;
            clearPosition();
            return;
        }
        replaceCurrentTrack(playbackMode == PlaybackMode.RANDOM
                ? tracks.get(random.nextInt(tracks.size()))
                : tracks.getFirst());
    }

    private void replaceCurrentTrack(String trackId) {
        currentTrackId = trackId;
        clearPosition();
    }

    private void clearPosition() {
        observedPositionSeconds = 0;
        reportedPositionSeconds = null;
    }

    private String nextSequential(List<String> tracks) {
        if (currentTrackId == null) {
            return tracks.getFirst();
        }

        int currentIndex = tracks.indexOf(currentTrackId);
        if (currentIndex < 0) {
            return tracks.getFirst();
        }

        return tracks.get((currentIndex + 1) % tracks.size());
    }

    private Optional<Long> currentTrackDurationSeconds() {
        return Optional.ofNullable(currentTrackId)
                .flatMap(catalogService::findTrackById)
                .map(Track::durationSeconds);
    }

    private List<String> activeTrackIds() {
        return playlistService.getActivePlaylist().trackIds();
    }

    private PlaybackState snapshot() {
        return new PlaybackState(
                playlistService.getActivePlaylist().id(),
                currentTrackId,
                playbackMode,
                transportState,
                reportedPositionSeconds != null ? reportedPositionSeconds : observedPositionSeconds
        );
    }
}

