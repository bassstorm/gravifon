package com.gravifon.player.registry.stream;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@ApplicationRing
@Slf4j
@Service
public class StreamTrackRefresher {
    private final StreamResolverRegistry resolverRegistry;
    private final TrackRegistry trackRegistry;
    private final StreamRefreshProperties properties;
    private final Executor executor;
    private final ConcurrentHashMap<String, CompletableFuture<StreamTrack>> refreshes = new ConcurrentHashMap<>();

    public StreamTrackRefresher(
            StreamResolverRegistry resolverRegistry,
            TrackRegistry trackRegistry,
            StreamRefreshProperties properties,
            @Qualifier("applicationTaskExecutor") Executor executor
    ) {
        this.resolverRegistry = resolverRegistry;
        this.trackRegistry = trackRegistry;
        this.properties = properties;
        this.executor = executor;
    }

    public StreamTrack ensureValid(StreamTrack track, Duration margin) throws IOException {
        if (track == null || margin == null || margin.isNegative()) {
            throw new IllegalArgumentException("A stream track and non-negative refresh margin are required");
        }
        Instant now = Instant.now();
        if (!track.needsRefresh(now, margin)) {
            return track;
        }

        CompletableFuture<StreamTrack> refresh = new CompletableFuture<>();
        CompletableFuture<StreamTrack> existing = refreshes.putIfAbsent(track.id(), refresh);
        if (existing != null) {
            return await(existing);
        }
        try {
            return refresh(track, margin, refresh);
        } catch (IOException | RuntimeException failure) {
            refresh.completeExceptionally(failure);
            throw failure;
        } finally {
            refreshes.remove(track.id(), refresh);
        }
    }

    private StreamTrack refresh(StreamTrack requestedTrack, Duration margin, CompletableFuture<StreamTrack> inFlight)
            throws IOException {
        StreamTrack current = latestTrack(requestedTrack);
        if (!current.needsRefresh(Instant.now(), margin)) {
            inFlight.complete(current);
            return current;
        }

        Exception lastFailure = null;
        for (int attempt = 0; attempt < properties.refreshMaxAttempts(); attempt++) {
            try {
                StreamResolver resolver = resolverRegistry.resolverFor(current);
                StreamResolver.ResolvedStream resolved = refreshWithTimeout(resolver, current);
                validateResolvedStream(resolved);
                Track updated = trackRegistry.updateStream(current.id(), resolved.streamUrl(), resolved.expiresAfter());
                StreamTrack refreshed = (StreamTrack) updated;
                if (refreshed.expiresAfter() != null
                        && !refreshed.expiresAfter().isAfter(Instant.now().plus(properties.refreshAhead()))) {
                    log.warn(
                            "Refreshed stream URL remains inside look-ahead window: trackId={}, expiresAfter={}",
                            refreshed.id(),
                            refreshed.expiresAfter()
                    );
                }
                inFlight.complete(refreshed);
                return refreshed;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Stream refresh interrupted", interrupted);
            } catch (Exception failure) {
                lastFailure = failure;
            }
        }

        current = latestTrack(current);
        if (hasUsableUrl(current, Instant.now())) {
            log.warn(
                    "Stream refresh failed; continuing with still-valid URL: trackId={}, reason={}",
                    current.id(),
                    failureMessage(lastFailure)
            );
            inFlight.complete(current);
            return current;
        }

        boolean timedOut = isTimeout(lastFailure);
        String errorKind = timedOut ? "STREAM_REFRESH_TIMEOUT" : "STREAM_UNREACHABLE";
        String message = timedOut ? "Stream refresh timed out" : failureMessage(lastFailure);
        IOException failure = new IOException(message);
        trackRegistry.markStreamUnreachable(current.id(), errorKind, message);
        log.error("Stream refresh failed for unusable URL: trackId={}, reason={}", current.id(), message);
        inFlight.completeExceptionally(failure);
        throw failure;
    }

    private StreamTrack latestTrack(StreamTrack fallback) {
        return trackRegistry
            .findTrackById(fallback.id())
            .filter(StreamTrack.class::isInstance)
            .map(StreamTrack.class::cast)
            .orElse(fallback);
    }

    private StreamResolver.ResolvedStream refreshWithTimeout(StreamResolver resolver, StreamTrack track)
            throws Exception {
        FutureTask<StreamResolver.ResolvedStream> attempt = new FutureTask<>(() -> resolver.refreshStream(track));
        executor.execute(attempt);
        try {
            return attempt.get(saturatedNanos(properties.refreshTimeout()), TimeUnit.NANOSECONDS);
        } catch (TimeoutException timeout) {
            attempt.cancel(true);
            throw timeout;
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw new IllegalStateException("Stream resolver failed", cause);
        }
    }

    private void validateResolvedStream(StreamResolver.ResolvedStream resolved) throws IOException {
        if (resolved == null || !StringUtils.hasText(resolved.streamUrl())) {
            throw new IOException("Resolver returned no stream URL");
        }
        Instant minimumExpiry = Instant.now().plus(properties.refreshMinValidity());
        if (resolved.expiresAfter() != null && !resolved.expiresAfter().isAfter(minimumExpiry)) {
            throw new IOException("Resolver returned a stream URL with insufficient validity");
        }
    }

    private boolean hasUsableUrl(StreamTrack track, Instant now) {
        return track.streamUrl() != null && (track.expiresAfter() == null || track.expiresAfter().isAfter(now));
    }

    private boolean isTimeout(Exception failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof TimeoutException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String failureMessage(Exception failure) {
        if (failure == null) {
            return "Stream refresh failed";
        }
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return StringUtils.hasText(message) ? message : cause.toString();
    }

    private StreamTrack await(CompletableFuture<StreamTrack> refresh) throws IOException {
        try {
            long timeoutNanos = maximumWaitNanos(properties.refreshTimeout(), properties.refreshMaxAttempts());
            return refresh.get(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for stream refresh", interrupted);
        } catch (TimeoutException timeout) {
            throw new IOException("Timed out waiting for stream refresh", timeout);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("Stream refresh failed", cause);
        }
    }

    static long maximumWaitNanos(Duration timeout, int attempts) {
        BigInteger timeoutNanos = BigInteger
            .valueOf(timeout.getSeconds())
            .multiply(BigInteger.valueOf(1_000_000_000L))
            .add(BigInteger.valueOf(timeout.getNano()));
        BigInteger maximumWait =
                timeoutNanos
            .multiply(BigInteger.valueOf(attempts))
            .add(BigInteger.valueOf(TimeUnit.SECONDS.toNanos(1)));
        return maximumWait.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }

    private static long saturatedNanos(Duration duration) {
        BigInteger nanos = BigInteger
            .valueOf(duration.getSeconds())
            .multiply(BigInteger.valueOf(1_000_000_000L))
            .add(BigInteger.valueOf(duration.getNano()));
        return nanos.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
    }
}
