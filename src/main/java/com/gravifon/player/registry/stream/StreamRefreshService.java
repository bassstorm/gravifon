package com.gravifon.player.registry.stream;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.stereotype.Service;

@ApplicationRing
@Service
@RequiredArgsConstructor
public class StreamRefreshService {
    private final StreamResolverRegistry resolverRegistry;
    private final TrackRegistry trackRegistry;
    private final TrackRepository trackRepository;
    private final PlaylistService playlistService;
    private final PlaybackService playbackService;
    private final GravifonProperties properties;
    private final ConcurrentHashMap<String, CompletableFuture<StreamTrack>> refreshes = new ConcurrentHashMap<>();

    public void refreshUpcomingStreams() {
        refreshUpcomingStreams(Instant.now());
    }

    public void refreshUpcomingStreams(Instant now) {
        if (!properties.getStreams().isRefreshEnabled()) {
            return;
        }
        Playlist active;
        try {
            active = playlistService.getActive();
        } catch (RuntimeException ignored) {
            return;
        }
        Instant threshold = now.plus(properties.getStreams().getRefreshAhead());
        Set<String> scope = new HashSet<>();
        if ("ACTIVE_PLAYLIST_PLUS_NEXT".equalsIgnoreCase(properties.getStreams().getRefreshScope())) {
            addPlaybackScope(scope, active);
        } else {
            scope.addAll(active.trackIds());
        }
        for (String trackId : scope) {
            trackRepository
                .findById(trackId)
                .ifPresent(track -> {
                    if (track instanceof StreamTrack streamTrack) {
                        if (streamTrack.expiresAfter() == null || !streamTrack.expiresAfter().isAfter(threshold)) {
                            try {
                                ensureFresh(streamTrack, now);
                            } catch (IOException ignored) {
                                // Request-time fallback retries after a bounded scheduler attempt.
                            }
                        }
                    }
                });
        }
    }

    private void addPlaybackScope(Set<String> scope, Playlist active) {
        PlaybackState state;
        try {
            state = playbackService.getState();
        } catch (RuntimeException ignored) {
            return;
        }
        if (state.currentTrackId() == null || state.playbackMode() != PlaybackMode.SEQUENTIAL) {
            return;
        }
        int currentIndex = active.trackIds().indexOf(state.currentTrackId());
        if (currentIndex >= 0 && !active.trackIds().isEmpty()) {
            scope.add(state.currentTrackId());
            scope.add(active.trackIds().get((currentIndex + 1) % active.trackIds().size()));
        }
    }

    public StreamTrack ensureFresh(StreamTrack track) throws IOException {
        return ensureFresh(track, Instant.now());
    }

    public StreamTrack ensureFresh(StreamTrack track, Instant evaluationTime) throws IOException {
        if (track.isStreamUrlFresh(evaluationTime)) {
            return track;
        }

        CompletableFuture<StreamTrack> refresh = new CompletableFuture<>();
        CompletableFuture<StreamTrack> existing = refreshes.putIfAbsent(track.id(), refresh);
        if (existing != null) {
            return await(existing);
        }
        try {
            StreamTrack current = trackRegistry
                .findTrackById(track.id())
                .filter(StreamTrack.class::isInstance)
                .map(StreamTrack.class::cast)
                .orElse(track);
            if (current.isStreamUrlFresh(evaluationTime)) {
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
                    Track updated =
                            trackRegistry.updateStream(current.id(), resolved.streamUrl(), resolved.expiresAfter());
                    StreamTrack updatedStream = (StreamTrack) updated;
                    refresh.complete(updatedStream);
                    return updatedStream;
                } catch (Exception failure) {
                    lastFailure = failure;
                }
            }
            String message = lastFailure == null ? "Stream refresh failed" : failureMessage(lastFailure);
            boolean timedOut = lastFailure instanceof TimeoutException
                    || lastFailure != null && lastFailure.getCause() instanceof TimeoutException;
            IOException failure = fail(
                    current,
                    timedOut ? "Stream refresh timed out" : message,
                    timedOut ? "STREAM_REFRESH_TIMEOUT" : "STREAM_UNREACHABLE"
            );
            refresh.completeExceptionally(failure);
            throw failure;
        } finally {
            refreshes.remove(track.id(), refresh);
        }
    }

    private StreamTrack await(CompletableFuture<StreamTrack> refresh) throws IOException {
        try {
            long timeout = properties.getStreams().getRefreshTimeout().toMillis() * Math.max(
                    1,
                    properties.getStreams().getRefreshMaxAttempts()
            )
                    + 1000;
            return refresh.get(timeout, TimeUnit.MILLISECONDS);
        } catch (Exception failure) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Stream refresh failed", cause);
        }
    }

    private IOException fail(StreamTrack track, String message, String errorKind) {
        trackRegistry.markStreamUnreachable(track.id(), errorKind, message);
        return new IOException(message);
    }

    private String failureMessage(Exception failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        return cause.getMessage() == null ? cause.toString() : cause.getMessage();
    }
}
