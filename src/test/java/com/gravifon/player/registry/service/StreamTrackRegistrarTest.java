package com.gravifon.player.registry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.stream.StreamResolutionException;
import com.gravifon.player.registry.stream.StreamResolver;
import com.gravifon.player.registry.stream.StreamResolverRegistry;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class StreamTrackRegistrarTest {
    @Mock
    private StreamRegistrationPersistence persistence;
    @Captor
    private ArgumentCaptor<List<StreamTrack>> resolvedTracks;

    @Test
    void resolvesAndValidatesAllTracksBeforePersistenceAndPreservesResolverOrder() {
        String sourceUrl = "https://example.com/album";
        StreamResolver resolver = new StubResolver(Map.of(
                sourceUrl,
                List.of(
                        resolved(sourceUrl + "/1", "One"),
                        resolved(sourceUrl + "/2", "Two"),
                        resolved(sourceUrl + "/1", "One")
                )
        ));
        StreamTrack first = streamTrack("one");
        StreamTrack second = streamTrack("two");
        when(persistence.registerResolvedTracks(any())).thenReturn(List.of(first, second, first));

        List<Track> registered = registrar(resolver).registerSources(List.of(uri(sourceUrl)));

        assertThat(registered).containsExactly(first, second, first);
        verify(persistence).registerResolvedTracks(resolvedTracks.capture());
        assertThat(resolvedTracks.getValue())
            .extracting(StreamTrack::metadata)
            .extracting(metadata -> metadata.values().get("TITLE").getFirst())
            .containsExactly("One", "Two", "One");
    }

    @Test
    void resolverFailureDoesNotInvokePersistenceForAnyEarlierResults() {
        String firstSource = "https://example.com/album";
        String failingSource = "https://failure.example/album";
        StreamResolver successful = new StubResolver(Map.of(firstSource, List.of(resolved(firstSource + "/1", "One"))));
        StreamResolver failing = new FailingResolver(failingSource);

        assertThatThrownBy(() -> registrar(successful, failing)
            .registerSources(List.of(uri(firstSource), uri(failingSource))))
            .isInstanceOf(StreamResolutionException.class)
            .hasMessage("Stream resolver failed to return valid tracks");

        verify(persistence, never()).registerResolvedTracks(any());
    }

    @Test
    void rejectsEmptyResolverResultsInsteadOfReturningAnEmptySuccess() {
        String sourceUrl = "https://example.com/empty";
        StreamResolver resolver = new StubResolver(Map.of(sourceUrl, List.of()));

        assertThatThrownBy(() -> registrar(resolver).registerSources(List.of(uri(sourceUrl))))
            .isInstanceOf(StreamResolutionException.class);

        verify(persistence, never()).registerResolvedTracks(any());
    }

    @Test
    void delegatesSchemeAndSupportDecisionsToResolverRegistry() {
        assertThatThrownBy(() -> registrar().registerSources(List.of(uri("custom://unsupported.example/audio"))))
            .isInstanceOf(StreamResolverRegistry.NoStreamResolverException.class);
        verify(persistence, never()).registerResolvedTracks(any());
    }

    @Test
    void resolverMayAcceptNonHttpSourceSchemes() {
        String sourceUrl = "custom://example.com/album";
        StreamResolver resolver =
                new StubResolver(Map.of(sourceUrl, List.of(resolved("custom://example.com/track", "Custom scheme"))));
        StreamTrack result = streamTrack("custom-track");
        when(persistence.registerResolvedTracks(any())).thenReturn(List.of(result));

        assertThat(registrar(resolver).registerSources(List.of(uri(sourceUrl)))).containsExactly(result);
        verify(persistence).registerResolvedTracks(any());
    }

    @Test
    void propagatesTrackIdentityConflictWithoutRetryingOrResolvingAgain() {
        String sourceUrl = "https://example.com/album";
        StubResolver resolver = new StubResolver(Map.of(sourceUrl, List.of(resolved(sourceUrl + "/1", "One"))));
        DataIntegrityViolationException collision = new DataIntegrityViolationException("duplicate track identity");
        when(persistence.registerResolvedTracks(any())).thenThrow(collision);

        assertThatThrownBy(() -> registrar(resolver).registerSources(List.of(uri(sourceUrl)))).isSameAs(collision);

        verify(persistence).registerResolvedTracks(any());
        assertThat(resolver.resolutionCalls).isEqualTo(1);
    }

    @Test
    void doesNotRetryUnrelatedPersistenceFailures() {
        String sourceUrl = "https://example.com/album";
        DataIntegrityViolationException failure = new DataIntegrityViolationException("metadata constraint failed");
        when(persistence.registerResolvedTracks(any())).thenThrow(failure);

        assertThatThrownBy(() -> registrar(
                new StubResolver(Map.of(sourceUrl, List.of(resolved(sourceUrl + "/1", "One"))))
        )
            .registerSources(List.of(uri(sourceUrl))))
            .isSameAs(failure);

        verify(persistence).registerResolvedTracks(any());
    }

    private StreamTrackRegistrar registrar(StreamResolver... resolvers) {
        return new StreamTrackRegistrar(new StreamResolverRegistry(List.of(resolvers)), persistence);
    }

    private static StreamResolver.ResolvedTrack resolved(String sourceUrl, String title) {
        return new StreamResolver.ResolvedTrack(
                URI.create(sourceUrl),
                new TrackMetadata(Map.of("TITLE", List.of(title))),
                100L
        );
    }

    private static URI uri(String sourceUrl) {
        return URI.create(sourceUrl);
    }

    private static StreamTrack streamTrack(String suffix) {
        return new StreamTrack(
                suffix,
                new TrackMetadata(Map.of("TITLE", List.of(suffix))),
                100L,
                com.gravifon.player.registry.model.TrackState.healthy(),
                java.net.URI.create("https://example.com/" + suffix),
                null,
                null
        );
    }

    private static class StubResolver implements StreamResolver {
        private final Map<String, List<ResolvedTrack>> results;
        private int resolutionCalls;

        private StubResolver(Map<String, List<ResolvedTrack>> results) {
            this.results = results;
        }

        @Override
        public boolean supports(URI sourceUrl) {
            return results.containsKey(sourceUrl.toString());
        }

        @Override
        public List<ResolvedTrack> resolveTracks(URI sourceUrl) {
            resolutionCalls++;
            return results.get(sourceUrl.toString());
        }

        @Override
        public ResolvedStream refreshStream(StreamTrack track) {
            return new ResolvedStream(URI.create("https://cdn.example/audio"), Instant.EPOCH.plusSeconds(60));
        }
    }

    private static class FailingResolver implements StreamResolver {
        private final String sourceUrl;

        private FailingResolver(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        @Override
        public boolean supports(URI candidate) {
            return sourceUrl.equals(candidate.toString());
        }

        @Override
        public List<ResolvedTrack> resolveTracks(URI ignored) {
            throw new IllegalStateException("resolver failed");
        }

        @Override
        public ResolvedStream refreshStream(StreamTrack track) {
            return new ResolvedStream(URI.create("https://cdn.example/audio"), Instant.EPOCH.plusSeconds(60));
        }
    }
}
