package com.gravifon.player.playback.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.playback.model.PlaybackMode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrackSelectorTest {
    @Test
    void sequentialTrackSelector_advancesSequentiallyAndWraps() {
        SequentialTrackSelector selector = new SequentialTrackSelector();
        List<String> tracks = List.of("t1", "t2", "t3");

        assertThat(selector.mode()).isEqualTo(PlaybackMode.SEQUENTIAL);
        assertThat(selector.selectInitialTrack(List.of())).isEmpty();
        assertThat(selector.selectInitialTrack(tracks)).contains("t1");

        assertThat(selector.selectNextTrack(tracks, "t1")).contains("t2");
        assertThat(selector.selectNextTrack(tracks, "t2")).contains("t3");
        assertThat(selector.selectNextTrack(tracks, "t3")).contains("t1");
        assertThat(selector.selectNextTrack(tracks, "unknown")).contains("t1");
        assertThat(selector.selectNextTrack(List.of(), "t1")).isEmpty();
    }

    @Test
    void sequentialUpcomingSelectsCurrentThenSuccessorsAndWrapsWithoutRepeats() {
        SequentialTrackSelector selector = new SequentialTrackSelector();
        List<String> tracks = List.of("t1", "t2", "t3");

        assertThat(selector.upcoming(tracks, "t3", 2)).containsExactly("t3", "t1");
        assertThat(selector.upcoming(tracks, "t2", 10)).containsExactly("t2", "t3", "t1");
        assertThat(selector.upcoming(List.of(), "t1", 1)).isEmpty();
        assertThat(selector.upcoming(tracks, "unknown", 2)).isEmpty();
        assertThat(selector.upcoming(tracks, "t1", 0)).isEmpty();
        assertThat(selector.upcoming(List.of("t1", "t1", "t2"), "t1", 3)).containsExactly("t1", "t2");
    }

    @Test
    void randomTrackSelector_selectsElementFromTracks() {
        RandomTrackSelector selector = new RandomTrackSelector();
        List<String> tracks = List.of("t1", "t2", "t3");

        assertThat(selector.mode()).isEqualTo(PlaybackMode.RANDOM);
        assertThat(selector.selectInitialTrack(List.of())).isEmpty();
        assertThat(selector.selectNextTrack(List.of(), "t1")).isEmpty();

        Optional<String> initial = selector.selectInitialTrack(tracks);
        assertThat(initial).isPresent();
        assertThat(tracks).contains(initial.get());

        Optional<String> next = selector.selectNextTrack(tracks, "t1");
        assertThat(next).isPresent();
        assertThat(tracks).contains(next.get());

        assertThat(selector.upcoming(tracks, "t2", 10)).containsExactly("t2");
        assertThat(selector.upcoming(tracks, "unknown", 10)).isEmpty();
    }

    @Test
    void trackSelectorRegistry_resolvesMatchingSelector() {
        SequentialTrackSelector sequential = new SequentialTrackSelector();
        RandomTrackSelector random = new RandomTrackSelector();
        TrackSelectorRegistry registry = new TrackSelectorRegistry(List.of(sequential, random));

        assertThat(registry.forMode(PlaybackMode.SEQUENTIAL)).isSameAs(sequential);
        assertThat(registry.forMode(PlaybackMode.RANDOM)).isSameAs(random);
    }
}
