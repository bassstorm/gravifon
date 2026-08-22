package com.gravifon.player.registry.stream;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

class StreamRefreshServiceTest {

    private final StreamResolverRegistry resolverRegistry = mock(StreamResolverRegistry.class);
    private final TrackRegistry trackRegistry = mock(TrackRegistry.class);
    private final StreamRefreshService service = new StreamRefreshService(resolverRegistry, trackRegistry,
            new GravifonProperties(),
            Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));

    @Test
    void freshUrlBypassesResolver() throws IOException {
        Track track = track("t1", Instant.parse("2026-01-01T00:01:00Z"));

        assertEquals(track, service.ensureFresh(track));
        verify(resolverRegistry, never()).resolverFor(any(Track.class));
    }

    @Test
    void expiredUrlRefreshesAndPersistsResolvedUrl() throws IOException {
        Track track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
        StreamResolver resolver = mock(StreamResolver.class);
        Track refreshed = track("t1", Instant.parse("2026-01-01T00:01:00Z"));
        when(resolverRegistry.resolverFor(track)).thenReturn(resolver);
        when(resolver.refreshStream(track)).thenReturn(new StreamResolver.ResolvedStream("https://fresh", refreshed.expiresAfter()));
        when(trackRegistry.findTrackById("t1")).thenReturn(Optional.of(track));
        when(trackRegistry.updateStream("t1", "https://fresh", refreshed.expiresAfter())).thenReturn(refreshed);

        assertEquals(refreshed, service.ensureFresh(track));
        verify(trackRegistry).updateStream("t1", "https://fresh", refreshed.expiresAfter());
    }

    @Test
    void failedRefreshMarksTrackAndDoesNotUpdateUrl() {
        Track track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
        StreamResolver resolver = mock(StreamResolver.class);
        when(resolverRegistry.resolverFor(track)).thenReturn(resolver);
        when(resolver.refreshStream(track)).thenThrow(new IllegalStateException("upstream down"));
        when(trackRegistry.findTrackById("t1")).thenReturn(Optional.of(track));

        assertThrows(IOException.class, () -> service.ensureFresh(track));
        verify(trackRegistry).markStreamUnreachable("t1", "STREAM_UNREACHABLE", "upstream down");
        verify(trackRegistry, never()).updateStream(any(), any(), any());
    }

    @Test
    void slowRefreshTimesOutAndMarksTrackWithoutUpdatingUrl() throws Exception {
        Track track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
        StreamResolver resolver = mock(StreamResolver.class);
        GravifonProperties properties = new GravifonProperties();
        properties.getStreams().setRefreshTimeout(java.time.Duration.ofMillis(10));
        properties.getStreams().setRefreshMaxAttempts(1);
        StreamRefreshService shortTimeoutService = new StreamRefreshService(resolverRegistry, trackRegistry,
                properties, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
        when(resolverRegistry.resolverFor(track)).thenReturn(resolver);
        when(trackRegistry.findTrackById("t1")).thenReturn(Optional.of(track));
        when(resolver.refreshStream(track)).thenAnswer(invocation -> {
            Thread.sleep(100);
            return new StreamResolver.ResolvedStream("https://late", Instant.parse("2026-01-01T00:01:00Z"));
        });

        assertThrows(IOException.class, () -> shortTimeoutService.ensureFresh(track));
        verify(trackRegistry).markStreamUnreachable("t1", "STREAM_REFRESH_TIMEOUT", "Stream refresh timed out");
        verify(trackRegistry, never()).updateStream(any(), any(), any());
    }

    @Test
    void concurrentRefreshSharesOneInFlightRefresh() throws Exception {
        Track track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
        StreamResolver resolver = mock(StreamResolver.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(resolverRegistry.resolverFor(track)).thenReturn(resolver);
        when(trackRegistry.findTrackById("t1")).thenReturn(Optional.of(track));
        when(resolver.refreshStream(track)).thenAnswer(invocation -> {
            entered.countDown();
            release.await(1, TimeUnit.SECONDS);
            return new StreamResolver.ResolvedStream("https://fresh", Instant.parse("2026-01-01T00:01:00Z"));
        });

        CompletableFuture<Track> first = CompletableFuture.supplyAsync(() -> refresh(track));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, entered.getCount()));
        CompletableFuture<Track> second = CompletableFuture.supplyAsync(() -> refresh(track));
        release.countDown();
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(first::isDone);
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(second::isDone);
        org.mockito.Mockito.verify(resolver, org.mockito.Mockito.times(1)).refreshStream(track);
    }

    private Track refresh(Track track) {
        try { return service.ensureFresh(track); }
        catch (IOException exception) { throw new RuntimeException(exception); }
    }

    private Track track(String id, Instant expiresAfter) {
        return new Track(id, TrackKind.STREAM, Map.of(), null, TrackState.healthy(),
                null, "mp3", "https://source", "https://old", expiresAfter);
    }
}