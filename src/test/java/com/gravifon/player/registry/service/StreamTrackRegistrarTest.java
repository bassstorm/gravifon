package com.gravifon.player.registry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.stream.StreamResolver;
import com.gravifon.player.registry.stream.StreamResolverRegistry;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StreamTrackRegistrarTest {

    @Test
    void expandsSourcesAndReusesExistingTrackIdentity() {
        TrackRepository repository = mock(TrackRepository.class);
        Map<String, Track> stored = new HashMap<>();
        StreamResolver resolver = new StubResolver();
        StreamTrackRegistrar registrar =
                new StreamTrackRegistrar(repository, new StreamResolverRegistry(List.of(resolver)));
        when(repository.findById(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(repository.save(org.mockito.ArgumentMatchers.any(Track.class))).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            stored.put(track.id(), track);
            return track;
        });

        List<Track> tracks = registrar.registerSources(List.of("https://example.com/album"));
        List<Track> readded = registrar.registerSources(List.of("https://example.com/album"));

        assertThat(tracks)
                .hasSize(2)
                .extracting(Track::kind)
                .containsOnly(com.gravifon.player.registry.model.TrackKind.STREAM);
        assertThat(readded)
                .extracting(Track::id)
                .containsExactlyElementsOf(tracks.stream().map(Track::id).toList());
        verify(repository, times(2)).save(org.mockito.ArgumentMatchers.any(Track.class));
    }

    @Test
    void failsWhenNoResolverSupportsSource() {
        TrackRepository repository = mock(TrackRepository.class);
        StreamTrackRegistrar registrar = new StreamTrackRegistrar(repository, new StreamResolverRegistry(List.of()));

        assertThatThrownBy(() -> registrar.registerSources(List.of("https://unsupported.example/song")))
                .isInstanceOf(StreamResolverRegistry.NoStreamResolverException.class);
    }

    private static class StubResolver implements StreamResolver {
        @Override
        public boolean supports(String sourceUrl) {
            return sourceUrl.contains("example.com");
        }

        @Override
        public List<ResolvedTrack> resolveTracks(String sourceUrl) {
            return List.of(
                    new ResolvedTrack(sourceUrl + "/1", Map.of("TITLE", List.of("One")), 100L),
                    new ResolvedTrack(sourceUrl + "/2", Map.of("TITLE", List.of("Two")), 110L));
        }

        @Override
        public ResolvedStream refreshStream(Track track) {
            return new ResolvedStream("https://cdn.example/audio", Instant.now().plusSeconds(60));
        }
    }
}
