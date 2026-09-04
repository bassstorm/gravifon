package com.gravifon.player.registry.repository;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackId;
import java.util.List;
import java.util.Optional;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public interface TrackRepository {
    List<Track> findAll();

    Optional<Track> findById(String id);

    default Optional<Track> findById(TrackId id) {
        return findById(id.value());
    }

    boolean existsById(String id);

    default boolean existsById(TrackId id) {
        return existsById(id.value());
    }

    List<Track> findAllById(Iterable<String> ids);

    default List<Track> findAllById(List<TrackId> ids) {
        return findAllById(ids.stream().map(TrackId::value).toList());
    }

    Track save(Track track);

    void deleteById(String id);

    default void deleteById(TrackId id) {
        deleteById(id.value());
    }
}
