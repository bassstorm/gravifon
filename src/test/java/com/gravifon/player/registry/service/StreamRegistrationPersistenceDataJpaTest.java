package com.gravifon.player.registry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gravifon.player.registry.identity.TrackIdentity;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackErrorReporter;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({TrackRepository.class, StreamRegistrationPersistence.class})
class StreamRegistrationPersistenceDataJpaTest {
    @Autowired
    private TrackRepository tracks;
    @Autowired
    private StreamRegistrationPersistence persistence;

    @Test
    void reusesExistingTracksAndPreservesMetadataStateAndOrderedDuplicateReferences() {
        String firstId = id("https://example.com/one");
        String secondId = id("https://example.com/two");
        StreamTrack existing = new StreamTrack(
                firstId,
                new TrackMetadata(Map.of("TITLE", List.of("Original title"))),
                120L,
                new TrackState(
                        true,
                        new TrackError("UPSTREAM", "Unavailable", Instant.EPOCH, TrackErrorReporter.CLIENT)
                ),
                URI.create("https://example.com/one"),
                URI.create("https://cdn.example/one"),
                Instant.EPOCH.plusSeconds(60)
        );
        tracks.save(existing);

        StreamTrack changedCandidate = new StreamTrack(
                firstId,
                new TrackMetadata(Map.of("TITLE", List.of("Replacement title"))),
                999L,
                TrackState.healthy(),
                URI.create("https://example.com/one?utm_source=changed"),
                null,
                null
        );
        StreamTrack second = streamTrack(secondId, "https://example.com/two", "Second");

        List<Track> registered = persistence.registerResolvedTracks(List.of(changedCandidate, second, changedCandidate));

        assertThat(registered).containsExactly(existing, second, existing);
        assertThat(tracks.findById(firstId)).contains(existing);
        assertThat(tracks.findAll()).hasSize(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistenceFailureRollsBackEveryNewTrack() {
        StreamTrack valid = streamTrack("valid", "https://example.com/valid", "Valid");
        StreamTrack invalid = streamTrack("invalid", "https://example.com/invalid", "x".repeat(256));

        assertThatThrownBy(() -> persistence.registerResolvedTracks(List.of(valid, invalid)))
            .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(tracks.findById("valid")).isEmpty();
        assertThat(tracks.findById("invalid")).isEmpty();
    }

    private static String id(String sourceUrl) {
        return TrackIdentity.forStream(URI.create(sourceUrl));
    }

    private static StreamTrack streamTrack(String id, String sourceUrl, String title) {
        return new StreamTrack(
                id,
                new TrackMetadata(Map.of("TITLE", List.of(title))),
                120L,
                TrackState.healthy(),
                URI.create(sourceUrl),
                null,
                null
        );
    }
}
