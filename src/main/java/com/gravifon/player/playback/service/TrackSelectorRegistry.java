package com.gravifon.player.playback.service;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.TrackSelector;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TrackSelectorRegistry {
    private final Map<PlaybackMode, TrackSelector> selectors;

    public TrackSelectorRegistry(List<TrackSelector> selectors) {
        this.selectors = selectors.stream().collect(Collectors.toUnmodifiableMap(TrackSelector::mode, s -> s));
    }

    public TrackSelector forMode(PlaybackMode mode) {
        TrackSelector selector = selectors.get(mode);
        if (selector == null) {
            throw new IllegalArgumentException("Unsupported playback mode: " + mode);
        }
        return selector;
    }
}
