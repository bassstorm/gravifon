package com.gravifon.player.playlist.repository;

import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.model.PlaylistId;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public interface PlaylistRepository {
    List<Playlist> findAll();

    Optional<Playlist> findById(String id);

    default Optional<Playlist> findById(PlaylistId id) {
        return findById(id.value());
    }

    Playlist save(Playlist playlist);

    void deleteById(String id);

    default void deleteById(PlaylistId id) {
        deleteById(id.value());
    }
}
