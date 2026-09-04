package com.gravifon.player.playback.service;

import com.gravifon.player.error.ResourceNotFoundException;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackSession;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TrackSelector;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.playback.repository.PlaybackStateRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlaybackService {

    private final PlaylistService playlistService;
    private final TrackRepository trackRepository;
    private final PlaybackStateRepository stateRepository;
    private final TrackSelectorRegistry selectorRegistry;
    private static final String SESSION_ID = "default";

    private PlaybackSession session = PlaybackSession.initial(SESSION_ID);

    @PostConstruct
    void restoreState() {
        stateRepository.find(SESSION_ID).ifPresent(state -> {
            PlaybackMode mode = PlaybackMode.SEQUENTIAL;
            if (state.activePlaylistId() != null) {
                try {
                    playlistService.activate(state.activePlaylistId());
                    var pl = playlistService.getPlaylist(state.activePlaylistId());
                    if (pl != null) {
                        mode = pl.playbackMode();
                    }
                } catch (ResourceNotFoundException ignored) {
                    return;
                }
            }
            PlaybackState enrichedState = new PlaybackState(state.activePlaylistId(), state.currentTrackId(),
                    mode, state.transportState(), state.positionSeconds(), state.positionOrigin());
            session = PlaybackSession.fromState(SESSION_ID, enrichedState);
        });
    }

    public synchronized PlaybackState getState() {
        return session.toPlaybackState();
    }

    public synchronized PlaybackState initializeClient() {
        session = session.initializeClient();
        persist();
        return session.toPlaybackState();
    }

    public synchronized PlaybackState selectPlaylist(String playlistId) {
        Playlist playlist = playlistService.select(playlistId);
        TrackSelector selector = selectorRegistry.forMode(playlist.playbackMode());
        session = session.selectPlaylist(playlist.id(), playlist.playbackMode(), playlist.trackIds(), selector);
        persist();
        return session.toPlaybackState();
    }

    public synchronized PlaybackState selectTrack(String trackId) {
        session = session.selectTrack(trackId, activeTrackIds());
        persist();
        return session.toPlaybackState();
    }

    public synchronized PlaybackState setMode(PlaybackMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Playback mode is required");
        }
        Playlist active = playlistService.getActive();
        playlistService.setMode(active.id(), mode);
        session = session.setMode(mode);
        persist();
        return session.toPlaybackState();
    }

    public synchronized PlaybackState setTransportState(TransportState transportState) {
        TrackSelector selector = selectorRegistry.forMode(session.playbackMode());
        session = session.setTransportState(transportState, activeTrackIds(), selector);
        persist();
        return session.toPlaybackState();
    }

    public synchronized PlaybackState nextTrack() {
        TrackSelector selector = selectorRegistry.forMode(session.playbackMode());
        session = session.nextTrack(activeTrackIds(), selector);
        persist();
        return session.toPlaybackState();
    }

    public synchronized PlaybackState reportPosition(String trackId, long positionSeconds) {
        Long duration = currentTrackDurationSeconds().orElse(null);
        session = session.reportPosition(trackId, positionSeconds, duration);
        persist();
        return session.toPlaybackState();
    }

    public synchronized void observeStream(String trackId, long endByte, long totalBytes) {
        currentTrackDurationSeconds().ifPresent(duration -> {
            session = session.observeStream(trackId, endByte, totalBytes, duration);
            persist();
        });
    }

    private Optional<Long> currentTrackDurationSeconds() {
        return Optional.ofNullable(session.currentTrackId())
                .flatMap(trackRepository::findById)
                .map(com.gravifon.player.registry.model.Track::durationSeconds);
    }

    private List<String> activeTrackIds() {
        try {
            return playlistService.getActive().trackIds();
        } catch (ResourceNotFoundException e) {
            return List.of();
        }
    }

    private void persist() {
        if (playlistService.activeId().isPresent()) {
            PlaybackState state = session.toPlaybackState();
            stateRepository.save(SESSION_ID, state, state.positionOrigin());
        }
    }
}

