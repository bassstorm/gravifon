package com.gravifon.player.playlist.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.playback.model.PlaybackMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaylistTest {
    @Test
    void reorderReplacesTrackOrderWithoutMutatingOriginal() {
        Playlist original = new Playlist("p1", "Mix", List.of("a", "b"), PlaybackMode.RANDOM);

        Playlist reordered = original.reorder(List.of("b", "a"));

        assertThat(reordered.trackIds()).containsExactly("b", "a");
        assertThat(reordered.name()).isEqualTo("Mix");
        assertThat(reordered.playbackMode()).isEqualTo(PlaybackMode.RANDOM);
        assertThat(original.trackIds()).containsExactly("a", "b");
    }

    @Test
    void addEntriesAppendsEntriesWithoutMutatingOriginal() {
        Playlist original = new Playlist("p1", "Mix", List.of("a"));

        Playlist updated = original.addEntries(List.of("b", "c"));

        assertThat(updated.trackIds()).containsExactly("a", "b", "c");
        assertThat(original.trackIds()).containsExactly("a");
    }

    @Test
    void removeEntriesRemovesOneMatchingOccurrencePerRemoval() {
        Playlist original = new Playlist("p1", "Mix", List.of("a", "b", "a"));

        Playlist updated = original.removeEntries(List.of("a"));

        assertThat(updated.trackIds()).containsExactly("b", "a");
        assertThat(original.trackIds()).containsExactly("a", "b", "a");
    }

    @Test
    void duplicateEntriesArePreservedRatherThanDeduplicated() {
        Playlist original = new Playlist("p1", "Mix", List.of("a", "a"));

        Playlist updated = original.addEntries(List.of("a"));

        assertThat(updated.trackIds()).containsExactly("a", "a", "a");
    }

    @Test
    void renameAndModeChangePreserveOtherPlaylistState() {
        Playlist original = new Playlist("p1", "Mix", List.of("a", "b"));

        Playlist renamed = original.rename("Favorites");
        Playlist randomized = renamed.setMode(PlaybackMode.RANDOM);

        assertThat(renamed.name()).isEqualTo("Favorites");
        assertThat(renamed.playbackMode()).isEqualTo(PlaybackMode.SEQUENTIAL);
        assertThat(randomized.name()).isEqualTo("Favorites");
        assertThat(randomized.trackIds()).containsExactly("a", "b");
        assertThat(randomized.playbackMode()).isEqualTo(PlaybackMode.RANDOM);
        assertThat(original.name()).isEqualTo("Mix");
    }

    @Test
    void constructorCopiesTrackIdsAndDefaultsNullMode() {
        List<String> trackIds = new ArrayList<>(List.of("a"));

        Playlist playlist = new Playlist("p1", "Mix", trackIds, null);
        trackIds.add("b");

        assertThat(playlist.trackIds()).containsExactly("a");
        assertThat(playlist.playbackMode()).isEqualTo(PlaybackMode.SEQUENTIAL);
    }
}
