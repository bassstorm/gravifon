package com.gravifon.player.registry.stream;

import com.gravifon.player.registry.model.StreamTrack;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class StreamResolverRegistry {
    private final List<StreamResolver> resolvers;

    public StreamResolverRegistry(List<StreamResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    public StreamResolver resolverFor(URI sourceUrl) {
        return resolvers
            .stream()
            .filter(resolver -> resolver.supports(sourceUrl))
            .findFirst()
            .orElseThrow(() -> new NoStreamResolverException(sourceUrl.toString()));
    }

    public StreamResolver resolverFor(StreamTrack track) {
        return resolverFor(track.sourceUrl());
    }

    public static class NoStreamResolverException extends RuntimeException {
        public NoStreamResolverException(String source) {
            super("No stream resolver supports: " + source);
        }
    }
}
