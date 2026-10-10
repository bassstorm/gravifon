package com.gravifon.player.registry.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class StreamTrackRefresherTest {
    private static final Duration MARGIN = Duration.ofMinutes(1);

    @Test
    void maximumWaitNanosSaturatesInsteadOfOverflowing() {
        assertThat(StreamTrackRefresher.maximumWaitNanos(Duration.ofSeconds(Long.MAX_VALUE), 2)).isEqualTo(
                Long.MAX_VALUE
        );
    }

    @Test
    void retriesAndPersistsAUsableResolvedStream() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            StreamResolverRegistry resolvers = mock(StreamResolverRegistry.class);
            TrackRegistry registry = mock(TrackRegistry.class);
            StreamTrack track = track("t1", "https://expired", Instant.now().minusSeconds(1));
            StreamTrack updated = track("t1", "https://fresh", Instant.now().plus(Duration.ofHours(1)));
            StreamResolver resolver = mock(StreamResolver.class);
            when(registry.findTrackById("t1")).thenReturn(Optional.of(track));
            when(resolvers.resolverFor(track)).thenReturn(resolver);
            when(resolver.refreshStream(track))
                .thenThrow(new IllegalStateException("temporary resolver failure"))
                .thenReturn(new StreamResolver.ResolvedStream("https://fresh", updated.expiresAfter()));
            when(registry.updateStream("t1", "https://fresh", updated.expiresAfter())).thenReturn(updated);
            StreamTrackRefresher refresher =
                    refresher(resolvers, registry, properties(Duration.ofSeconds(1), 2), executor);

            assertThat(refresher.ensureValid(track, MARGIN)).isEqualTo(updated);
            verify(resolver, org.mockito.Mockito.times(2)).refreshStream(track);
            verify(registry).updateStream("t1", "https://fresh", updated.expiresAfter());
        }
    }

    @Test
    void timesOutResolverAttemptAndMarksExpiredTrack() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            StreamResolverRegistry resolvers = mock(StreamResolverRegistry.class);
            TrackRegistry registry = mock(TrackRegistry.class);
            StreamTrack track = track("t1", "https://expired", Instant.now().minusSeconds(1));
            StreamResolver resolver = mock(StreamResolver.class);
            when(registry.findTrackById("t1")).thenReturn(Optional.of(track));
            when(resolvers.resolverFor(track)).thenReturn(resolver);
            when(resolver.refreshStream(track)).thenAnswer(invocation -> {
                Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                return new StreamResolver.ResolvedStream("https://late", Instant.now().plus(Duration.ofHours(1)));
            });
            StreamTrackRefresher refresher =
                    refresher(resolvers, registry, properties(Duration.ofMillis(20), 1), executor);

            assertThatThrownBy(() -> refresher.ensureValid(track, MARGIN))
                .isInstanceOf(IOException.class)
                .hasMessage("Stream refresh timed out");
            verify(registry).markStreamUnreachable("t1", "STREAM_REFRESH_TIMEOUT", "Stream refresh timed out");
            verify(registry, never()).updateStream(any(), any(), any());
        }
    }

    @Test
    void concurrentCallersShareOneRefresh() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            StreamResolverRegistry resolvers = mock(StreamResolverRegistry.class);
            TrackRegistry registry = mock(TrackRegistry.class);
            StreamTrack track = track("t1", "https://nearly-expired", Instant.now().plusSeconds(10));
            StreamTrack updated = track("t1", "https://fresh", Instant.now().plus(Duration.ofHours(1)));
            StreamResolver resolver = mock(StreamResolver.class);
            CountDownLatch enteredResolver = new CountDownLatch(1);
            CountDownLatch releaseResolver = new CountDownLatch(1);
            when(registry.findTrackById("t1")).thenReturn(Optional.of(track));
            when(resolvers.resolverFor(track)).thenReturn(resolver);
            when(resolver.refreshStream(track)).thenAnswer(invocation -> {
                enteredResolver.countDown();
                if (!releaseResolver.await(1, TimeUnit.SECONDS)) {
                    throw new IOException("test resolver was not released");
                }
                return new StreamResolver.ResolvedStream("https://fresh", updated.expiresAfter());
            });
            when(registry.updateStream("t1", "https://fresh", updated.expiresAfter())).thenReturn(updated);
            StreamTrackRefresher refresher =
                    refresher(resolvers, registry, properties(Duration.ofSeconds(1), 2), executor);

            Future<StreamTrack> first = executor.submit(() -> refresher.ensureValid(track, MARGIN));
            assertThat(enteredResolver.await(1, TimeUnit.SECONDS)).isTrue();
            Future<StreamTrack> second = executor.submit(() -> refresher.ensureValid(track, MARGIN));
            releaseResolver.countDown();

            assertThat(first.get(2, TimeUnit.SECONDS)).isEqualTo(updated);
            assertThat(second.get(2, TimeUnit.SECONDS)).isEqualTo(updated);
            verify(resolver).refreshStream(track);
        }
    }

    @Test
    void rejectsResolvedUrlThatCannotMeetMinimumValidity() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            StreamResolverRegistry resolvers = mock(StreamResolverRegistry.class);
            TrackRegistry registry = mock(TrackRegistry.class);
            StreamTrack track = track("t1", "https://expired", Instant.now().minusSeconds(1));
            StreamResolver resolver = mock(StreamResolver.class);
            when(registry.findTrackById("t1")).thenReturn(Optional.of(track));
            when(resolvers.resolverFor(track)).thenReturn(resolver);
            when(resolver.refreshStream(track))
                .thenReturn(new StreamResolver.ResolvedStream("https://too-short", Instant.now().plusSeconds(5)));
            StreamTrackRefresher refresher =
                    refresher(resolvers, registry, properties(Duration.ofSeconds(1), 1), executor);

            assertThatThrownBy(() -> refresher.ensureValid(track, MARGIN))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("insufficient validity");
            verify(registry)
                .markStreamUnreachable(
                        "t1",
                        "STREAM_UNREACHABLE",
                        "Resolver returned a stream URL with insufficient validity"
                );
            verify(registry, never()).updateStream(any(), any(), any());
        }
    }

    @Test
    void failedRefreshServesStillValidUrlWithoutMarkingFailure() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            StreamResolverRegistry resolvers = mock(StreamResolverRegistry.class);
            TrackRegistry registry = mock(TrackRegistry.class);
            StreamTrack track = track("t1", "https://still-valid", Instant.now().plusSeconds(30));
            StreamResolver resolver = mock(StreamResolver.class);
            when(registry.findTrackById("t1")).thenReturn(Optional.of(track));
            when(resolvers.resolverFor(track)).thenReturn(resolver);
            when(resolver.refreshStream(track)).thenThrow(new IllegalStateException("upstream down"));
            StreamTrackRefresher refresher =
                    refresher(resolvers, registry, properties(Duration.ofSeconds(1), 1), executor);

            assertThat(refresher.ensureValid(track, MARGIN)).isEqualTo(track);
            verify(registry, never()).markStreamUnreachable(any(), any(), any());
            verify(registry, never()).updateStream(any(), any(), any());
        }
    }

    private StreamTrackRefresher refresher(
            StreamResolverRegistry resolvers,
            TrackRegistry registry,
            StreamRefreshProperties properties,
            ExecutorService executor
    ) {
        return new StreamTrackRefresher(resolvers, registry, properties, executor);
    }

    private StreamRefreshProperties properties(Duration timeout, int attempts) {
        return new StreamRefreshProperties(Duration.ofMinutes(2), 1, Duration.ofSeconds(10), timeout, attempts);
    }

    private StreamTrack track(String id, String streamUrl, Instant expiresAfter) {
        return new StreamTrack(id, Map.of(), null, TrackState.healthy(), "https://source", streamUrl, expiresAfter);
    }
}
