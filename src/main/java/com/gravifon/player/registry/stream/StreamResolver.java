package com.gravifon.player.registry.stream;

import com.gravifon.player.registry.identity.DefaultSourceUrlNormalizer;
import com.gravifon.player.registry.identity.SourceUrlNormalizer;
import com.gravifon.player.registry.model.Track;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public interface StreamResolver {

    boolean supports(String sourceUrl);

    default SourceUrlNormalizer normalizer() {
        return new DefaultSourceUrlNormalizer();
    }

    List<ResolvedTrack> resolveTracks(String sourceUrl);

    ResolvedStream refreshStream(Track track);

    record ResolvedTrack(String sourceUrl, Map<String, List<String>> metadata, Long durationSeconds) {}

    record ResolvedStream(String streamUrl, Instant expiresAfter) {}
}
