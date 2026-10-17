package com.gravifon.player.registry.stream;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.TrackMetadata;
import java.net.URI;
import java.time.Instant;
import java.util.List;

public interface StreamResolver {
    boolean supports(URI sourceUrl);

    List<ResolvedTrack> resolveTracks(URI sourceUrl);

    ResolvedStream refreshStream(StreamTrack track);

    record ResolvedTrack(URI sourceUrl, TrackMetadata metadata, Long durationSeconds) {}

    record ResolvedStream(URI streamUrl, Instant expiresAfter) {}
}
