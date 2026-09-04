package com.gravifon.player.playback.service;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.TrackSelector;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
public class RandomTrackSelector implements TrackSelector {

    @Override
    public PlaybackMode mode() {
        return PlaybackMode.RANDOM;
    }

    @Override
    public Optional<String> selectInitialTrack(List<String> trackIds) {
        return selectRandom(trackIds);
    }

    @Override
    public Optional<String> selectNextTrack(List<String> trackIds, String currentTrackId) {
        return selectRandom(trackIds);
    }

    private Optional<String> selectRandom(List<String> trackIds) {
        if (trackIds.isEmpty()) {
            return Optional.empty();
        }
        int index = ThreadLocalRandom.current().nextInt(trackIds.size());
        return Optional.of(trackIds.get(index));
    }
}
