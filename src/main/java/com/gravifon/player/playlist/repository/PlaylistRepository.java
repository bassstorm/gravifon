package com.gravifon.player.playlist.repository;

import com.gravifon.player.playlist.model.Playlist;
import java.util.List;
import java.util.Optional;

public interface PlaylistRepository {

    List<Playlist> findAll();

    Optional<Playlist> findById(String id);

    Playlist save(Playlist playlist);

    void deleteById(String id);
}