package com.gravifon.player.registry.stream;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamRefreshServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final StreamResolverRegistry resolverRegistry = mock(StreamResolverRegistry.class);
    private final TrackRegistry trackRegistry = mock(TrackRegistry.class);
    private final TrackRepository trackRepository = mock(TrackRepository.class);
    private final PlaylistService playlistService = mock(PlaylistService.class);
    private final PlaybackService playbackService = mock(PlaybackService.class);
    private final StreamRefreshService service = new StreamRefreshService(resolverRegistry, trackRegistry,
            trackRepository, playlistService, playbackService, new GravifonProperties());

    @Test
    void freshUrlBypassesResolver() throws IOException {
        StreamTrack track = track("t1", Instant.parse("2026-01-01T00:01:00Z"));

        assertEquals(track, service.ensureFresh(track, NOW));
        verify(resolverRegistry, never()).resolverFor(any(Track.class));
    }

    @Test
    void expiredUrlRefreshesAndPersistsResolvedUrl() throws IOException {
        StreamTrack track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
        StreamResolver resolver = mock(StreamResolver.class);
        StreamTrack refreshed = track("t1", Instant.parse("2026-01-01T00:01:00Z"));
        when(resolverRegistry.resolverFor(track)).thenReturn(resolver);
        when(resolver.refreshStream(track)).thenReturn(new StreamResolver.ResolvedStream("https://fresh", refreshed.expiresAfter()));
        when(trackRegistry.findTrackById("t1")).thenReturn(Optional.of(track));
        when(trackRegistry.updateStream("t1", "https://fresh", refreshed.expiresAfter())).thenReturn(refreshed);

        assertEquals(refreshed, service.ensureFresh(track, NOW));
        verify(trackRegistry).updateStream("t1", "https://fresh", refreshed.expiresAfter());
    }

    @Test
    void failedRefreshMarksTrackAndDoesNotUpdateUrl() {
        StreamTrack track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
        StreamResolver resolver = mock(StreamResolver.class);
        when(resolverRegistry.resolverFor(track)).thenReturn(resolver);
        when(resolver.refreshStream(track)).thenThrow(new IllegalStateException("upstream down"));
        when(trackRegistry.findTrackById("t1")).thenReturn(Optional.of(track));

        assertThrows(IOException.class, () -> service.ensureFresh(track, NOW));
        verify(trackRegistry).markStreamUnreachable("t1", "STREAM_UNREACHABLE", "upstream down");
        verify(trackRegistry, never()).updateStream(any(), any(), any());
    }

    @Test
    void slowRefreshTimesOutAndMarksTrackWithoutUpdatingUrl() throws Exception {
        StreamTrack track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
        StreamResolver resolver = mock(StreamResolver.class);
        GravifonProperties properties = new GravifonProperties();
        properties.getStreams().setRefreshTimeout(java.time.Duration.ofMillis(10));
        properties.getStreams().setRefreshMaxAttempts(1);
        StreamRefreshService shortTimeoutService = new StreamRefreshService(resolverRegistry, trackRegistry,
                trackRepository, playlistService, playbackService, properties);
        when(resolverRegistry.resolverFor(track)).thenReturn(resolver);
        when(trackRegistry.findTrackById("t1")).thenReturn(Optional.of(track));
        when(resolver.refreshStream(track)).thenAnswer(invocation -> {
            Thread.sleep(100);
            return new StreamResolver.ResolvedStream("https://late", Instant.parse("2026-01-01T00:01:00Z"));
        });

        assertThrows(IOException.class, () -> shortTimeoutService.ensureFresh(track, NOW));
        verify(trackRegistry).markStreamUnreachable("t1", "STREAM_REFRESH_TIMEOUT", "Stream refresh timed out");
        verify(trackRegistry, never()).updateStream(any(), any(), any());
    }

    @Test
    void concurrentRefreshSharesOneInFlightRefresh() throws Exception {
        StreamTrack track = track("t1", Instant.parse("2025-12-31T23:59:59Z"));
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

        CompletableFuture<StreamTrack> first = CompletableFuture.supplyAsync(() -> refresh(track));
        Awaitility.await().atMost(1, TimeUnit.SECONDS).untilAsserted(() -> assertEquals(0, entered.getCount()));
        CompletableFuture<StreamTrack> second = CompletableFuture.supplyAsync(() -> refresh(track));
        release.countDown();
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(first::isDone);
        Awaitility.await().atMost(2, TimeUnit.SECONDS).until(second::isDone);
        org.mockito.Mockito.verify(resolver, org.mockito.Mockito.times(1)).refreshStream(track);
    }

    private StreamTrack refresh(StreamTrack track) {
        try { return service.ensureFresh(track); }
        catch (IOException exception) { throw new RuntimeException(exception); }
    }

    private StreamTrack track(String id, Instant expiresAfter) {
        return new StreamTrack(id, Map.of(), null, TrackState.healthy(),
                "https://source", "https://old", expiresAfter);
    }
}