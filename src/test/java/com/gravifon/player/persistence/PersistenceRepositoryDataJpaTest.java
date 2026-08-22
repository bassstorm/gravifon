package com.gravifon.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playback.repository.JpaPlaybackStateRepository;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.repository.JpaPlaylistRepository;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.JpaTrackRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({
        com.gravifon.player.config.SqliteDataSourceConfiguration.class,
        JpaTrackRepository.class,
        JpaPlaylistRepository.class,
        JpaPlaybackStateRepository.class
})
class PersistenceRepositoryDataJpaTest {

    private static final String CONFIG_DIR = "/tmp/gravifon-jpa-test-" + System.nanoTime();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private JpaTrackRepository tracks;

    @Autowired
    private JpaPlaylistRepository playlists;

    @Autowired
    private JpaPlaybackStateRepository playbackStates;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("gravifon.config-dir", () -> CONFIG_DIR);
        registry.add("gravifon.music-root", () -> "/tmp/music");
    }

    @Test
    void trackMetadataRoundTripPreservesValuesAndOrdering() {
        Track track = fileTrack("track-1");
        Track saved = tracks.save(track);

        assertThat(saved.metadata()).containsEntry("GENRE", List.of("ambient", "downtempo"));
        assertThat(tracks.findById("track-1")).get().satisfies(reloaded ->
                assertThat(reloaded.metadata()).containsEntry("GENRE", List.of("ambient", "downtempo")));
    }

    @Test
    void playlistRoundTripPreservesEntryPositionsAndUpdatesExistingRows() {
        tracks.save(fileTrack("track-1"));
        tracks.save(fileTrack("track-2"));
        Playlist initial = playlists.save(new Playlist("playlist-1", "Mix", List.of("track-2", "track-1")));
        Playlist updated = playlists.save(new Playlist("playlist-1", "Renamed", List.of("track-1"), PlaybackMode.RANDOM));

        assertThat(initial.trackIds()).containsExactly("track-2", "track-1");
        assertThat(playlists.findById("playlist-1")).get().satisfies(reloaded -> {
            assertThat(reloaded.name()).isEqualTo("Renamed");
            assertThat(reloaded.trackIds()).containsExactly("track-1");
            assertThat(reloaded.playbackMode()).isEqualTo(PlaybackMode.RANDOM);
        });
    }

    @Test
    void deletingTrackCascadesMetadataRows() {
        tracks.save(fileTrack("track-3"));
        tracks.deleteById("track-3");

        assertThat(tracks.findById("track-3")).isEmpty();
    }

    @Test
    void playbackStateSaveUpsertsSessionAndPreservesOrigin() {
        PlaybackState first = new PlaybackState("playlist-1", "track-1", PlaybackMode.SEQUENTIAL,
                TransportState.PAUSED, 12, "OBSERVED");
        PlaybackState second = new PlaybackState("playlist-1", "track-1", PlaybackMode.SEQUENTIAL,
                TransportState.PAUSED, 18, "REPORTED");

        playbackStates.save("default", first, first.positionOrigin());
        playbackStates.save("default", second, second.positionOrigin());

        assertThat(playbackStates.find("default")).get().satisfies(state -> {
            assertThat(state.positionSeconds()).isEqualTo(18);
            assertThat(state.positionOrigin()).isEqualTo("REPORTED");
        });
    }

    private Track fileTrack(String id) {
        return new Track(id, TrackKind.FILE,
                Map.of("GENRE", List.of("ambient", "downtempo")), 120L,
                TrackState.healthy(), id + ".mp3", "mp3", null, null, null);
    }
}
