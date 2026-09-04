package com.gravifon.player.registry.stream;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import java.util.List;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.stereotype.Component;

@ApplicationRing
@Component
public class StreamResolverRegistry {

    private final List<StreamResolver> resolvers;

    public StreamResolverRegistry(List<StreamResolver> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    public StreamResolver resolverFor(String sourceUrl) {
        return resolvers.stream()
                .filter(resolver -> resolver.supports(sourceUrl))
                .findFirst()
                .orElseThrow(() -> new NoStreamResolverException(sourceUrl));
    }

    public StreamResolver resolverFor(Track track) {
        if (track instanceof StreamTrack streamTrack) {
            return resolverFor(streamTrack.sourceUrl());
        }
        throw new NoStreamResolverException(track.id());
    }

    public static class NoStreamResolverException extends RuntimeException {
        public NoStreamResolverException(String source) {
            super("No stream resolver supports: " + source);
        }
    }
}
