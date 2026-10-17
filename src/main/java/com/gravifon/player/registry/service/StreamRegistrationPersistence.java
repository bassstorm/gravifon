package com.gravifon.player.registry.service;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.repository.TrackRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StreamRegistrationPersistence {
    private final TrackRepository tracks;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Track> registerResolvedTracks(List<StreamTrack> resolvedTracks) {
        if (resolvedTracks == null || resolvedTracks.isEmpty() || resolvedTracks.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("Resolved tracks are required");
        }

        tracks.lockCreation();
        Map<String, Track> tracksById = new LinkedHashMap<>();
        for (StreamTrack candidate : resolvedTracks) {
            tracksById.computeIfAbsent(candidate.id(), id -> tracks
                .findById(id)
                .orElseGet(() -> tracks.save(candidate)));
        }
        return resolvedTracks
            .stream()
            .map(track -> tracksById.get(track.id()))
            .toList();
    }
}
