package com.gravifon.player.registry.repository;

import com.gravifon.player.persistence.AdmissionScope;
import com.gravifon.player.persistence.JpaAdmissionLockStore;
import com.gravifon.player.persistence.JpaTrackStore;
import com.gravifon.player.registry.model.Track;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class TrackRepository {
    private final JpaTrackStore store;
    private final JpaAdmissionLockStore admissionLocks;

    @Transactional(readOnly = true)
    public List<Track> findAll() {
        return store.findAll().stream().map(TrackConverter::toDomain).toList();
    }

    @Transactional(readOnly = true)
    public Optional<Track> findById(String id) {
        return store.findById(id).map(TrackConverter::toDomain);
    }

    @Transactional(readOnly = true)
    public boolean existsById(String id) {
        return store.existsById(id);
    }

    @Transactional(readOnly = true)
    public List<Track> findAllById(Collection<String> ids) {
        return store.findAllById(ids).stream().map(TrackConverter::toDomain).toList();
    }

    @Transactional
    public Track save(Track track) {
        return TrackConverter.toDomain(store.save(TrackConverter.toEntity(track)));
    }

    @Transactional
    public List<Track> saveAll(Collection<Track> tracks) {
        return store
            .saveAll(tracks.stream().map(TrackConverter::toEntity).toList())
            .stream()
            .map(TrackConverter::toDomain)
            .toList();
    }

    @Transactional
    public void deleteById(String id) {
        store.deleteById(id);
    }

    @Transactional
    public void deleteAllById(Collection<String> ids) {
        store.deleteAllById(ids);
    }

    @Transactional
    public void lockCreation() {
        admissionLocks.lock(AdmissionScope.TRACK);
    }
}
