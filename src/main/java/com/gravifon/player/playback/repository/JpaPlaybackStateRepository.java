package com.gravifon.player.playback.repository;

import com.gravifon.player.persistence.JpaPlaybackStateStore;
import com.gravifon.player.persistence.PlaybackStateEntity;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import java.util.Optional;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Primary
public class JpaPlaybackStateRepository implements PlaybackStateRepository {
    private final JpaPlaybackStateStore store;
    private final PlaylistRepository playlistRepository;
    private final Clock clock;
    @Autowired
    public JpaPlaybackStateRepository(JpaPlaybackStateStore store, PlaylistRepository playlistRepository) {
        this(store, playlistRepository, Clock.systemUTC());
    }
    public JpaPlaybackStateRepository(JpaPlaybackStateStore store, PlaylistRepository playlistRepository, Clock clock) {
        this.store = store;
        this.playlistRepository = playlistRepository;
        this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public Optional<PlaybackState> find(String sessionId) {
        return store.findById(sessionId).map(entity -> new PlaybackState(entity.activePlaylistId(),
            entity.currentTrackId(), entity.activePlaylistId() == null ? PlaybackMode.SEQUENTIAL
                : playlistRepository.findById(entity.activePlaylistId())
                .map(com.gravifon.player.playlist.model.Playlist::playbackMode)
                .orElse(PlaybackMode.SEQUENTIAL),
            TransportState.valueOf(entity.transportState()), entity.positionSeconds(),
            entity.positionOrigin() == null ? "OBSERVED" : entity.positionOrigin()));
    }
    @Override @Transactional
    public void save(String sessionId, PlaybackState state, String positionOrigin) {
        store.save(new PlaybackStateEntity(sessionId, state.activePlaylistId(), state.currentTrackId(),
                state.transportState().name(), state.positionSeconds(), state.positionOrigin(),
                clock.instant().getEpochSecond()));
    }
}
