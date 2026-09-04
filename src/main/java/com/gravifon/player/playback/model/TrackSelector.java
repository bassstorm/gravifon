package com.gravifon.player.playback.model;

import java.util.List;
import java.util.Optional;

public interface TrackSelector {

    PlaybackMode mode();

    Optional<String> selectInitialTrack(List<String> trackIds);

    Optional<String> selectNextTrack(List<String> trackIds, String currentTrackId);
}
