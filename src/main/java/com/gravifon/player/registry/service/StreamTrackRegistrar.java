package com.gravifon.player.registry.service;

import com.gravifon.player.registry.identity.TrackIdentity;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.stream.StreamResolutionException;
import com.gravifon.player.registry.stream.StreamResolver;
import com.gravifon.player.registry.stream.StreamResolverRegistry;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StreamTrackRegistrar {
    private final StreamResolverRegistry resolverRegistry;
    private final StreamRegistrationPersistence persistence;

    public StreamTrackRegistrar(StreamResolverRegistry resolverRegistry, StreamRegistrationPersistence persistence) {
        this.resolverRegistry = resolverRegistry;
        this.persistence = persistence;
    }

    public List<Track> registerSources(List<URI> sourceUrls) {
        List<StreamTrack> resolvedTracks = new ArrayList<>();
        for (URI sourceUrl : sourceUrls) {
            StreamResolver resolver = resolverRegistry.resolverFor(sourceUrl);
            List<StreamResolver.ResolvedTrack> sourceResults;
            try {
                sourceResults = resolver.resolveTracks(sourceUrl);
            } catch (RuntimeException exception) {
                throw new StreamResolutionException(exception);
            }
            if (sourceResults == null || sourceResults.isEmpty()) {
                throw new StreamResolutionException();
            }
            for (StreamResolver.ResolvedTrack resolved : sourceResults) {
                if (resolved == null) {
                    throw new StreamResolutionException();
                }
                try {
                    String id = TrackIdentity.forStream(resolved.sourceUrl());
                    resolvedTracks.add(
                            new StreamTrack(
                                    id,
                                    resolved.metadata(),
                                    resolved.durationSeconds(),
                                    TrackState.healthy(),
                                    resolved.sourceUrl(),
                                    null,
                                    null
                            )
                    );
                } catch (RuntimeException exception) {
                    throw new StreamResolutionException(exception);
                }
            }
        }
        return persistence.registerResolvedTracks(resolvedTracks);
    }
}
