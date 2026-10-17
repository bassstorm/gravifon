package com.gravifon.player.registry.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.TrackMetadata;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamResolverRegistryTest {
    @Test
    void routesSourcesToTheFirstSupportingResolver() {
        StreamResolver resolver = new StubResolver("example.com");
        StreamResolverRegistry registry = new StreamResolverRegistry(List.of(resolver));

        URI sourceUrl = URI.create("https://example.com/album");
        assertThat(registry.resolverFor(sourceUrl)).isSameAs(resolver);
        assertThat(resolver.resolveTracks(sourceUrl)).hasSize(2);
    }

    @Test
    void reportsUnsupportedSources() {
        StreamResolverRegistry registry = new StreamResolverRegistry(List.of(new StubResolver("example.com")));

        assertThatThrownBy(() -> registry.resolverFor(URI.create("https://other.example/song")))
            .isInstanceOf(StreamResolverRegistry.NoStreamResolverException.class);
    }

    private static class StubResolver implements StreamResolver {
        private final String host;

        private StubResolver(String host) {
            this.host = host;
        }

        @Override
        public boolean supports(URI sourceUrl) {
            return sourceUrl.toString().contains(host);
        }

        @Override
        public List<ResolvedTrack> resolveTracks(URI sourceUrl) {
            return List.of(
                    new ResolvedTrack(
                            URI.create(sourceUrl + "/1"),
                            new TrackMetadata(Map.of("TITLE", List.of("One"))),
                            100L
                    ),
                    new ResolvedTrack(
                            URI.create(sourceUrl + "/2"),
                            new TrackMetadata(Map.of("TITLE", List.of("Two"))),
                            110L
                    )
            );
        }

        @Override
        public ResolvedStream refreshStream(StreamTrack track) {
            return new ResolvedStream(URI.create("https://cdn.example/song"), Instant.now().plusSeconds(60));
        }
    }
}
