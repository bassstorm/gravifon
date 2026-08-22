package com.gravifon.player.playback.service;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.playback.repository.PlaybackStateRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlaybackService {

    private final PlaylistAccess playlistService;
    private final DurationLookup durationLookup;
    private final Random random;
    private final PlaybackStateRepository stateRepository;
    private static final String SESSION_ID = "default";

    private String currentTrackId;
    private PlaybackMode playbackMode = PlaybackMode.SEQUENTIAL;
    private TransportState transportState = TransportState.STOPPED;
    private long observedPositionSeconds;
    private Long reportedPositionSeconds;

    @Autowired
    public PlaybackService(PlaylistService playlistService, TrackRepository trackRepository,
                           PlaybackStateRepository stateRepository) {
        this(playlistService, trackRepository, new Random(), stateRepository);
    }

    PlaybackService(PlaylistService playlistService, TrackRepository trackRepository, Random random,
                    PlaybackStateRepository stateRepository) {
        this(new PlaylistAccess() {
            @Override
            public com.gravifon.player.playlist.model.Playlist getActivePlaylist() {
                return playlistService.getActive();
            }

            @Override
            public com.gravifon.player.playlist.model.Playlist selectPlaylist(String playlistId) {
                return playlistService.select(playlistId);
            }

            @Override
            public void activatePlaylist(String playlistId) {
                playlistService.activate(playlistId);
            }

            @Override
            public void setMode(String playlistId, PlaybackMode mode) {
                playlistService.setMode(playlistId, mode);
            }
        }, trackRepository::findById, random, stateRepository);
    }

    private PlaybackService(PlaylistAccess playlistService, DurationLookup durationLookup, Random random,
                            PlaybackStateRepository stateRepository) {
        this.playlistService = playlistService;
        this.durationLookup = durationLookup;
        this.random = random;
        this.stateRepository = stateRepository;
    }

    @PostConstruct
    void restoreState() {
        if (stateRepository == null) {
            return;
        }
        stateRepository.find(SESSION_ID).ifPresent(state -> {
            if (state.activePlaylistId() != null) {
                try {
                    playlistService.activatePlaylist(state.activePlaylistId());
                } catch (com.gravifon.player.api.error.ResourceNotFoundException ignored) {
                    return;
                }
            }
            currentTrackId = state.currentTrackId();
            transportState = state.transportState() == TransportState.PLAYING
                    ? TransportState.PAUSED : state.transportState();
            observedPositionSeconds = state.positionSeconds();
                reportedPositionSeconds = "REPORTED".equals(state.positionOrigin())
                    ? state.positionSeconds() : null;
        });
    }

    public synchronized PlaybackState getState() {
        return snapshot();
    }

    public synchronized PlaybackState initializeClient() {
        if (transportState == TransportState.PLAYING) {
            transportState = TransportState.PAUSED;
            persist();
        }
        return snapshot();
    }

    public synchronized PlaybackState selectPlaylist(String playlistId) {
        var playlist = playlistService.selectPlaylist(playlistId);
        playbackMode = playlist.playbackMode();
        selectInitialTrack();
        persist();
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
        playlistService.setMode(playlistService.getActivePlaylist().id(), mode);
        this.playbackMode = mode;
        persist();
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
        persist();
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
        persist();
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
        persist();
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
            persist();
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
                .flatMap(durationLookup::find)
                .map(com.gravifon.player.registry.model.Track::durationSeconds);
    }

    private List<String> activeTrackIds() {
        return playlistService.getActivePlaylist().trackIds();
    }

    private PlaybackState snapshot() {
        var activePlaylist = playlistService.getActivePlaylistOptional().orElse(null);
        boolean reported = reportedPositionSeconds != null;
        return new PlaybackState(
                activePlaylist == null ? null : activePlaylist.id(),
                currentTrackId,
                playbackMode,
                transportState,
                reported ? reportedPositionSeconds : observedPositionSeconds,
                reported ? "REPORTED" : "OBSERVED"
        );
    }

    private void persist() {
        if (stateRepository != null && playlistService.getActivePlaylistOptional().isPresent()) {
            PlaybackState state = snapshot();
            stateRepository.save(SESSION_ID, state, state.positionOrigin());
        }
    }

    private interface PlaylistAccess {
        com.gravifon.player.playlist.model.Playlist getActivePlaylist();
        default Optional<com.gravifon.player.playlist.model.Playlist> getActivePlaylistOptional() {
            try {
                return Optional.ofNullable(getActivePlaylist());
            } catch (com.gravifon.player.api.error.ResourceNotFoundException exception) {
                return Optional.empty();
            }
        }
        com.gravifon.player.playlist.model.Playlist selectPlaylist(String playlistId);
        void activatePlaylist(String playlistId);
        void setMode(String playlistId, PlaybackMode mode);
    }

    @FunctionalInterface
    private interface DurationLookup {
        Optional<com.gravifon.player.registry.model.Track> find(String trackId);
    }
}

