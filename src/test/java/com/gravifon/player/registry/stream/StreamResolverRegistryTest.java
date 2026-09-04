package com.gravifon.player.registry.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gravifon.player.registry.model.Track;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamResolverRegistryTest {
    @Test
    void routesSourcesToTheFirstSupportingResolver() {
        StreamResolver resolver = new StubResolver("example.com");
        StreamResolverRegistry registry = new StreamResolverRegistry(List.of(resolver));

        assertThat(registry.resolverFor("https://example.com/album")).isSameAs(resolver);
        assertThat(resolver.resolveTracks("https://example.com/album")).hasSize(2);
    }

    @Test
    void reportsUnsupportedSources() {
        StreamResolverRegistry registry = new StreamResolverRegistry(List.of(new StubResolver("example.com")));

        assertThatThrownBy(() -> registry.resolverFor("https://other.example/song"))
            .isInstanceOf(StreamResolverRegistry.NoStreamResolverException.class);
    }

    private static class StubResolver implements StreamResolver {
        private final String host;

        private StubResolver(String host) {
            this.host = host;
        }

        @Override
        public boolean supports(String sourceUrl) {
            return sourceUrl.contains(host);
        }

        @Override
        public List<ResolvedTrack> resolveTracks(String sourceUrl) {
            return List.of(
                    new ResolvedTrack(sourceUrl + "/1", Map.of("TITLE", List.of("One")), 100L),
                    new ResolvedTrack(sourceUrl + "/2", Map.of("TITLE", List.of("Two")), 110L)
            );
        }

        @Override
        public ResolvedStream refreshStream(Track track) {
            return new ResolvedStream("https://cdn.example/song", Instant.now().plusSeconds(60));
        }
    }
}
