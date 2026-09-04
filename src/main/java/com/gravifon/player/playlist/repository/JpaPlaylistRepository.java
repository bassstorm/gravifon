package com.gravifon.player.playlist.repository;

import com.gravifon.player.persistence.JpaPlaylistStore;
import com.gravifon.player.persistence.PlaylistEntity;
import com.gravifon.player.persistence.PlaylistEntryEntity;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playback.model.PlaybackMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@InfrastructureRing
@Repository
@Primary
@RequiredArgsConstructor
public class JpaPlaylistRepository implements PlaylistRepository {
    private final JpaPlaylistStore store;

    @Override @Transactional(readOnly = true)
    public List<Playlist> findAll() { return store.findAll().stream().map(this::toDomain).toList(); }
    @Override @Transactional(readOnly = true)
    public Optional<Playlist> findById(String id) { return store.findById(id).map(this::toDomain); }
    @Override @Transactional
    public Playlist save(Playlist playlist) {
        Instant now = Instant.now();
        PlaylistEntity entity = store.findById(playlist.id())
            .orElseGet(() -> new PlaylistEntity(playlist.id(), playlist.name(), playlist.playbackMode().name(), now));
        entity.update(playlist.name(), playlist.playbackMode().name(), now);
        entity.replaceEntries(IntStream.range(0, playlist.trackIds().size())
            .mapToObj(position -> new PlaylistEntryEntity(position, playlist.trackIds().get(position))).toList());
        return toDomain(store.save(entity));
    }
    @Override @Transactional
    public void deleteById(String id) { store.deleteById(id); }
    private Playlist toDomain(PlaylistEntity entity) {
        List<String> ids = entity.entries().stream().sorted(Comparator.comparingInt(PlaylistEntryEntity::position))
                .map(PlaylistEntryEntity::trackId).toList();
        return new Playlist(entity.id(), entity.name(), ids, PlaybackMode.valueOf(entity.playbackMode()));
    }
}
