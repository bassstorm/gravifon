package com.gravifon.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playback.repository.JpaPlaybackStateRepository;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.repository.JpaPlaylistRepository;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.JpaTrackRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

@DataJpaTest
@Import({JpaTrackRepository.class, JpaPlaylistRepository.class, JpaPlaybackStateRepository.class})
class PersistenceRepositoryDataJpaTest {
    @Autowired
    private JpaTrackRepository tracks;
    @Autowired
    private JpaPlaylistRepository playlists;
    @Autowired
    private JpaPlaybackStateRepository playbackStates;

    @Test
    void trackMetadataRoundTripPreservesValuesAndOrdering() {
        Track track = fileTrack("track-1");
        Track saved = tracks.save(track);

        assertThat(saved.metadata()).containsEntry("GENRE", List.of("ambient", "downtempo"));
        assertThat(tracks.findById("track-1"))
            .get()
            .satisfies(reloaded -> assertThat(reloaded.metadata())
                .containsEntry("GENRE", List.of("ambient", "downtempo")));
        assertThat(tracks.findById(com.gravifon.player.registry.model.TrackId.of("track-1"))).isPresent();
        assertThat(tracks.existsById("track-1")).isTrue();
        assertThat(tracks.existsById(com.gravifon.player.registry.model.TrackId.of("track-1"))).isTrue();
        assertThat(tracks.existsById("non-existent")).isFalse();
        assertThat(tracks.findAllById(List.of("track-1", "non-existent"))).hasSize(1);

        tracks.deleteById("track-1");
        assertThat(tracks.findById("track-1")).isEmpty();
        // Recreating track with new metadata proves no orphaned metadata rows survived
        tracks.save(fileTrack("track-1", Map.of("ARTIST", List.of("Solo Artist"))));
        assertThat(tracks.findById("track-1"))
            .get()
            .satisfies(reloaded -> {
                assertThat(reloaded.metadata()).containsOnlyKeys("ARTIST");
                assertThat(reloaded.metadata().get("ARTIST")).containsExactly("Solo Artist");
            });
    }

    @Test
    void playlistRoundTripPreservesEntryPositionsAndUpdatesExistingRows() {
        tracks.save(fileTrack("track-1"));
        tracks.save(fileTrack("track-2"));
        Playlist initial = playlists.save(new Playlist("playlist-1", "Mix", List.of("track-2", "track-1")));
        playlists.save(new Playlist("playlist-1", "Renamed", List.of("track-1"), PlaybackMode.RANDOM));

        assertThat(initial.trackIds()).containsExactly("track-2", "track-1");
        assertThat(playlists.findById("playlist-1"))
            .get()
            .satisfies(reloaded -> {
                assertThat(reloaded.name()).isEqualTo("Renamed");
                assertThat(reloaded.trackIds()).containsExactly("track-1");
                assertThat(reloaded.playbackMode()).isEqualTo(PlaybackMode.RANDOM);
            });
        assertThat(playlists.findById(com.gravifon.player.playlist.model.PlaylistId.of("playlist-1"))).isPresent();

        playlists.deleteById("playlist-1");
        assertThat(playlists.findById("playlist-1")).isEmpty();
        // Recreating playlist with different entries proves no orphaned entries survived
        playlists.save(new Playlist("playlist-1", "Clean", List.of("track-2")));
        assertThat(playlists.findById("playlist-1"))
            .get()
            .satisfies(reloaded -> assertThat(reloaded.trackIds()).containsExactly("track-2"));
    }

    @Test
    void deletingTrackRemovesTrackFromRepository() {
        tracks.save(fileTrack("track-3"));
        assertThat(tracks.findById("track-3")).isPresent();

        tracks.deleteById("track-3");
        assertThat(tracks.findById("track-3")).isEmpty();
    }

    @Test
    void sharedTrackMetadataUpdateIsVisibleThroughEveryPlaylistReference() {
        tracks.save(fileTrack("shared-track"));
        playlists.save(new Playlist("playlist-1", "First", List.of("shared-track")));
        playlists.save(new Playlist("playlist-2", "Second", List.of("shared-track")));

        tracks.save(fileTrack("shared-track", Map.of("GENRE", List.of("updated"))));

        for (String playlistId : List.of("playlist-1", "playlist-2")) {
            Playlist playlist = playlists.findById(playlistId).orElseThrow();
            Track referencedTrack = tracks.findById(playlist.trackIds().getFirst()).orElseThrow();
            assertThat(referencedTrack.metadata()).containsEntry("GENRE", List.of("updated"));
        }
    }

    @Test
    void playbackStateSaveUpsertsSessionAndPreservesOrigin() {
        PlaybackState first =
                new PlaybackState(
                        "playlist-1",
                        "track-1",
                        PlaybackMode.SEQUENTIAL,
                        TransportState.PAUSED,
                        12,
                        "OBSERVED"
        );
        PlaybackState second =
                new PlaybackState(
                        "playlist-1",
                        "track-1",
                        PlaybackMode.SEQUENTIAL,
                        TransportState.PAUSED,
                        18,
                        "REPORTED"
        );

        playbackStates.save("default", first, first.positionOrigin());
        assertThat(playbackStates.find("default"))
            .get()
            .satisfies(state -> assertThat(state.positionSeconds()).isEqualTo(12));
        playbackStates.save("default", second, second.positionOrigin());

        assertThat(playbackStates.find("default"))
            .get()
            .satisfies(state -> {
                assertThat(state.positionSeconds()).isEqualTo(18);
                assertThat(state.positionOrigin()).isEqualTo("REPORTED");
            });
    }

    private Track fileTrack(String id) {
        return fileTrack(id, Map.of("GENRE", List.of("ambient", "downtempo")));
    }

    private Track fileTrack(String id, Map<String, List<String>> metadata) {
        return new FileTrack(id, metadata, 120L, TrackState.healthy(), id + ".mp3", "mp3");
    }
}
