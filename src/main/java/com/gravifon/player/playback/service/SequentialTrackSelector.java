package com.gravifon.player.playback.service;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.TrackSelector;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SequentialTrackSelector implements TrackSelector {
    @Override
    public PlaybackMode mode() {
        return PlaybackMode.SEQUENTIAL;
    }

    @Override
    public Optional<String> selectInitialTrack(List<String> trackIds) {
        return trackIds.isEmpty() ? Optional.empty() : Optional.of(trackIds.getFirst());
    }

    @Override
    public Optional<String> selectNextTrack(List<String> trackIds, String currentTrackId) {
        if (trackIds.isEmpty()) {
            return Optional.empty();
        }
        if (currentTrackId == null) {
            return Optional.of(trackIds.getFirst());
        }
        int currentIndex = trackIds.indexOf(currentTrackId);
        if (currentIndex < 0) {
            return Optional.of(trackIds.getFirst());
        }
        return Optional.of(trackIds.get((currentIndex + 1) % trackIds.size()));
    }

    @Override
    public List<String> upcoming(List<String> trackIds, String currentTrackId, int limit) {
        if (trackIds.isEmpty() || currentTrackId == null || limit < 1) {
            return List.of();
        }
        int currentIndex = trackIds.indexOf(currentTrackId);
        if (currentIndex < 0) {
            return List.of();
        }

        LinkedHashSet<String> upcoming = new LinkedHashSet<>();
        int maxTracks = Math.min(limit, trackIds.size());
        for (int offset = 0; offset < trackIds.size() && upcoming.size() < maxTracks; offset++) {
            upcoming.add(trackIds.get((currentIndex + offset) % trackIds.size()));
        }
        return List.copyOf(upcoming);
    }
}
