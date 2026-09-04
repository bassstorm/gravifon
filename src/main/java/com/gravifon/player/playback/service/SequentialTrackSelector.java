package com.gravifon.player.playback.service;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.TrackSelector;
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
}
