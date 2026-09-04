package com.gravifon.player.playback.repository;

import com.gravifon.player.persistence.JpaPlaybackStateStore;
import com.gravifon.player.persistence.PlaybackStateEntity;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@InfrastructureRing
@Repository
@Primary
@RequiredArgsConstructor
public class JpaPlaybackStateRepository implements PlaybackStateRepository {
    private final JpaPlaybackStateStore store;

    @Override
    @Transactional(readOnly = true)
    public Optional<PlaybackState> find(String sessionId) {
        return store
            .findById(sessionId)
            .map(entity -> new PlaybackState(
                    entity.activePlaylistId(),
                    entity.currentTrackId(),
                    PlaybackMode.SEQUENTIAL,
                    TransportState.valueOf(entity.transportState()),
                    entity.positionSeconds(),
                    entity.positionOrigin() == null ? "OBSERVED" : entity.positionOrigin()
            ));
    }

    @Override
    @Transactional
    public void save(String sessionId, PlaybackState state, String positionOrigin) {
        store.save(
                new PlaybackStateEntity(
                        sessionId,
                        state.activePlaylistId(),
                        state.currentTrackId(),
                        state.transportState().name(),
                        state.positionSeconds(),
                        state.positionOrigin(),
                        Instant.now().getEpochSecond()
                )
        );
    }
}
