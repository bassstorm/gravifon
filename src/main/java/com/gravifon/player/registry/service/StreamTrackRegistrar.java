package com.gravifon.player.registry.service;

import com.gravifon.player.registry.identity.TrackIdentity;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.stream.StreamResolver;
import com.gravifon.player.registry.stream.StreamResolverRegistry;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class StreamTrackRegistrar {

    private final TrackRepository trackRepository;
    private final StreamResolverRegistry resolverRegistry;

    public StreamTrackRegistrar(TrackRepository trackRepository, StreamResolverRegistry resolverRegistry) {
        this.trackRepository = trackRepository;
        this.resolverRegistry = resolverRegistry;
    }

    public List<Track> registerSources(List<String> sourceUrls) {
        List<Track> tracks = new ArrayList<>();
        for (String sourceUrl : sourceUrls) {
            StreamResolver resolver = resolverRegistry.resolverFor(sourceUrl);
            for (StreamResolver.ResolvedTrack resolved : resolver.resolveTracks(sourceUrl)) {
                String id = TrackIdentity.forStream(resolved.sourceUrl(), resolver.normalizer());
                var existing = trackRepository.findById(id);
                if (existing.isPresent()) {
                    tracks.add(existing.get());
                } else {
                    Track track = new Track(id, TrackKind.STREAM, resolved.metadata(), resolved.durationSeconds(),
                            TrackState.healthy(), null, null, resolved.sourceUrl(), null, null);
                    tracks.add(trackRepository.save(track));
                }
            }
        }
        return List.copyOf(tracks);
    }
}