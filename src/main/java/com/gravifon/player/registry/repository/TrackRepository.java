package com.gravifon.player.registry.repository;

import com.gravifon.player.registry.model.Track;
import java.util.List;
import java.util.Optional;

public interface TrackRepository {

    List<Track> findAll();

    Optional<Track> findById(String id);

    Track save(Track track);

    void deleteById(String id);
}