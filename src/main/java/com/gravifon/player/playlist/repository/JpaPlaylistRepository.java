package com.gravifon.player.playlist.repository;

import com.gravifon.player.persistence.JpaPlaylistStore;
import com.gravifon.player.persistence.PlaylistEntity;
import com.gravifon.player.persistence.PlaylistEntryEntity;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playback.model.PlaybackMode;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Primary
public class JpaPlaylistRepository implements PlaylistRepository {
    private final JpaPlaylistStore store;
    private final Clock clock;
    @Autowired
    public JpaPlaylistRepository(JpaPlaylistStore store) { this(store, Clock.systemUTC()); }
    public JpaPlaylistRepository(JpaPlaylistStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }
    @Override @Transactional(readOnly = true)
    public List<Playlist> findAll() { return store.findAll().stream().map(this::toDomain).toList(); }
    @Override @Transactional(readOnly = true)
    public Optional<Playlist> findById(String id) { return store.findById(id).map(this::toDomain); }
    @Override @Transactional
    public Playlist save(Playlist playlist) {
        PlaylistEntity entity = store.findById(playlist.id())
            .orElseGet(() -> new PlaylistEntity(playlist.id(), playlist.name(), playlist.playbackMode().name(), clock.instant()));
        entity.update(playlist.name(), playlist.playbackMode().name(), clock.instant());
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
