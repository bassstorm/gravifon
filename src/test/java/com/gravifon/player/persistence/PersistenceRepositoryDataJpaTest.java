package com.gravifon.player.persistence;

import static org.assertj.core.api.Assertions.assertThat;

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
@Import(JpaTrackRepository.class)
class PersistenceRepositoryDataJpaTest {
    @Autowired
    private JpaTrackRepository tracks;

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
    void deletingTrackRemovesTrackFromRepository() {
        tracks.save(fileTrack("track-3"));
        assertThat(tracks.findById("track-3")).isPresent();

        tracks.deleteById("track-3");
        assertThat(tracks.findById("track-3")).isEmpty();
    }

    private Track fileTrack(String id) {
        return fileTrack(id, Map.of("GENRE", List.of("ambient", "downtempo")));
    }

    private Track fileTrack(String id, Map<String, List<String>> metadata) {
        return new FileTrack(id, metadata, 120L, TrackState.healthy(), id + ".mp3", "mp3");
    }
}
