package com.gravifon.player.registry.repository;

import com.gravifon.player.persistence.JpaTrackStore;
import com.gravifon.player.persistence.TrackEntity;
import com.gravifon.player.persistence.TrackMetadataEntity;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Primary
public class JpaTrackRepository implements TrackRepository {
    private final JpaTrackStore store;

    public JpaTrackRepository(JpaTrackStore store) {
        this.store = store;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Track> findAll() {
        return store.findAll().stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Track> findById(String id) {
        return store.findById(id).map(this::toDomain);
    }

    @Override
    @Transactional
    public Track save(Track track) {
        TrackEntity entity = new TrackEntity(track.id(), track.kind().name(), track.durationSeconds(), track.relPath(),
                track.format(), track.sourceUrl(), track.streamUrl(), epoch(track.expiresAfter()), track.state().failing(),
                errorKind(track), errorMessage(track), errorAt(track), errorReporter(track));
        List<TrackMetadataEntity> metadata = new ArrayList<>();
        track.metadata().forEach((key, values) -> {
            for (int order = 0; order < values.size(); order++) {
                metadata.add(new TrackMetadataEntity(key, values.get(order), order));
            }
        });
        entity.replaceMetadata(metadata);
        return toDomain(store.save(entity));
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        store.deleteById(id);
    }

    private Track toDomain(TrackEntity entity) {
        var metadata = new LinkedHashMap<String, List<String>>();
        entity.metadata().stream().sorted(java.util.Comparator.comparing(TrackMetadataEntity::key)
                .thenComparingInt(TrackMetadataEntity::order)).forEach(value ->
                metadata.computeIfAbsent(value.key(), ignored -> new ArrayList<>()).add(value.value()));
        TrackError error = entity.errorKind() == null ? null : new TrackError(entity.errorKind(), entity.errorMessage(),
                instant(entity.errorAt()), entity.errorReporter());
        return new Track(entity.id(), TrackKind.valueOf(entity.kind()), metadata, entity.durationSeconds(),
                new TrackState(entity.failing(), error), entity.relativePath(), entity.format(), entity.sourceUrl(),
                entity.streamUrl(), instant(entity.expiresAfter()));
    }

    private Long epoch(Instant value) { return value == null ? null : value.getEpochSecond(); }
    private Instant instant(Long value) { return value == null ? null : Instant.ofEpochSecond(value); }
    private String errorKind(Track track) { return track.state().lastError() == null ? null : track.state().lastError().kind(); }
    private String errorMessage(Track track) { return track.state().lastError() == null ? null : track.state().lastError().message(); }
    private Long errorAt(Track track) { return track.state().lastError() == null ? null : epoch(track.state().lastError().at()); }
    private String errorReporter(Track track) { return track.state().lastError() == null ? null : track.state().lastError().reportedBy(); }
}
