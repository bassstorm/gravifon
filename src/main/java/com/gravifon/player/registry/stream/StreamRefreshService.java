package com.gravifon.player.registry.stream;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.service.TrackRegistry;
import com.gravifon.player.config.GravifonProperties;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StreamRefreshService {

    private final StreamResolverRegistry resolverRegistry;
    private final TrackRegistry trackRegistry;
    private final GravifonProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, CompletableFuture<Track>> refreshes = new ConcurrentHashMap<>();

    @Autowired
    public StreamRefreshService(StreamResolverRegistry resolverRegistry, TrackRegistry trackRegistry,
                                GravifonProperties properties) {
        this(resolverRegistry, trackRegistry, properties, Clock.systemUTC());
    }

    StreamRefreshService(StreamResolverRegistry resolverRegistry, TrackRegistry trackRegistry,
                         GravifonProperties properties, Clock clock) {
        this.resolverRegistry = resolverRegistry;
        this.trackRegistry = trackRegistry;
        this.properties = properties;
        this.clock = clock;
    }

    public Track ensureFresh(Track track) throws IOException {
        if (track.isStreamUrlFresh(clock.instant())) {
            return track;
        }

        CompletableFuture<Track> refresh = new CompletableFuture<>();
        CompletableFuture<Track> existing = refreshes.putIfAbsent(track.id(), refresh);
        if (existing != null) {
            return await(existing);
        }
        try {
            Track current = trackRegistry.findTrackById(track.id()).orElse(track);
            if (current.isStreamUrlFresh(clock.instant())) {
                refresh.complete(current);
                return current;
            }
            Exception lastFailure = null;
            for (int attempt = 0; attempt < properties.getStreams().getRefreshMaxAttempts(); attempt++) {
                try {
                    StreamResolver resolver = resolverRegistry.resolverFor(current);
                    StreamResolver.ResolvedStream resolved = CompletableFuture
                            .supplyAsync(() -> resolver.refreshStream(current))
                            .get(properties.getStreams().getRefreshTimeout().toMillis(), TimeUnit.MILLISECONDS);
                    if (resolved == null || resolved.streamUrl() == null || resolved.streamUrl().isBlank()) {
                        throw new IOException("Resolver returned no stream URL");
                    }
                    Track updated = trackRegistry.updateStream(current.id(), resolved.streamUrl(), resolved.expiresAfter());
                    refresh.complete(updated);
                    return updated;
                } catch (Exception failure) {
                    lastFailure = failure;
                }
            }
                String message = lastFailure == null ? "Stream refresh failed" : failureMessage(lastFailure);
                boolean timedOut = lastFailure instanceof java.util.concurrent.TimeoutException
                    || lastFailure != null && lastFailure.getCause() instanceof java.util.concurrent.TimeoutException;
                IOException failure = fail(current, timedOut ? "Stream refresh timed out" : message,
                    timedOut ? "STREAM_REFRESH_TIMEOUT" : "STREAM_UNREACHABLE");
            refresh.completeExceptionally(failure);
            throw failure;
        } finally {
            refreshes.remove(track.id(), refresh);
        }
    }

    private Track await(CompletableFuture<Track> refresh) throws IOException {
        try {
            long timeout = properties.getStreams().getRefreshTimeout().toMillis()
                    * Math.max(1, properties.getStreams().getRefreshMaxAttempts()) + 1000;
            return refresh.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Stream refresh failed", cause);
        }
    }

    private IOException fail(Track track, String message, String errorKind) {
        trackRegistry.markStreamUnreachable(track.id(), errorKind, message);
        return new IOException(message);
    }

    private String failureMessage(Exception failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}