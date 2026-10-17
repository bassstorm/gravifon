package com.gravifon.player.registry.stream;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class StreamRefreshServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void plansDueTracksAndRefreshesSequentially() throws Exception {
        StreamRefreshProperties properties = properties();
        RefreshPlanner planner = new RefreshPlanner();
        StreamTrackRefresher refresher = mock(StreamTrackRefresher.class);
        StreamRefreshService service = new StreamRefreshService(planner, refresher, properties);
        StreamTrack later = track("later", NOW.plusSeconds(300));
        StreamTrack earlier = track("earlier", NOW.plusSeconds(60));
        StreamTrack notDue = track("not-due", NOW.plus(Duration.ofHours(2)));
        when(refresher.ensureValid(earlier, properties.refreshAhead())).thenReturn(earlier);
        when(refresher.ensureValid(later, properties.refreshAhead())).thenReturn(later);

        service.refreshDue(List.of(later, notDue, earlier), NOW);

        var order = org.mockito.Mockito.inOrder(refresher);
        order.verify(refresher).ensureValid(earlier, properties.refreshAhead());
        order.verify(refresher).ensureValid(later, properties.refreshAhead());
    }

    @Test
    void oneFailedTrackDoesNotPreventRefreshingTheNext() throws Exception {
        StreamRefreshProperties properties = properties();
        StreamTrackRefresher refresher = mock(StreamTrackRefresher.class);
        StreamRefreshService service = new StreamRefreshService(new RefreshPlanner(), refresher, properties);
        StreamTrack failed = track("failed", NOW.minusSeconds(1));
        StreamTrack next = track("next", NOW.plusSeconds(60));
        when(refresher.ensureValid(failed, properties.refreshAhead())).thenThrow(new IOException("upstream down"));
        when(refresher.ensureValid(next, properties.refreshAhead())).thenReturn(next);

        service.refreshDue(List.of(failed, next), NOW);

        verify(refresher).ensureValid(failed, properties.refreshAhead());
        verify(refresher).ensureValid(next, properties.refreshAhead());
    }

    @Test
    void refreshesStillValidUrlThatFallsInsideLookAheadWindow() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            StreamRefreshProperties properties = properties();
            Instant now = Instant.now();
            StreamTrack nearlyExpired = track("t1", now.plus(Duration.ofMinutes(30)));
            StreamTrack refreshed = new StreamTrack(
                    "t1",
                    new TrackMetadata(Map.of()),
                    null,
                    TrackState.healthy(),
                    nearlyExpired.sourceUrl(),
                    URI.create("https://fresh"),
                    now.plus(Duration.ofHours(2))
            );
            StreamResolverRegistry resolvers = mock(StreamResolverRegistry.class);
            TrackRegistry registry = mock(TrackRegistry.class);
            StreamResolver resolver = mock(StreamResolver.class);
            when(registry.findTrackById("t1")).thenReturn(Optional.of(nearlyExpired));
            when(resolvers.resolverFor(nearlyExpired)).thenReturn(resolver);
            when(resolver.refreshStream(nearlyExpired))
                .thenReturn(new StreamResolver.ResolvedStream(URI.create("https://fresh"), refreshed.expiresAfter()));
            when(registry.updateStream("t1", URI.create("https://fresh"), refreshed.expiresAfter())).thenReturn(
                    refreshed
            );
            StreamTrackRefresher refresher = new StreamTrackRefresher(resolvers, registry, properties, executor);
            StreamRefreshService service = new StreamRefreshService(new RefreshPlanner(), refresher, properties);

            service.refreshDue(List.of(nearlyExpired), now);

            verify(resolver).refreshStream(nearlyExpired);
            verify(registry).updateStream("t1", URI.create("https://fresh"), refreshed.expiresAfter());
        }
    }

    private StreamRefreshProperties properties() {
        return new StreamRefreshProperties(Duration.ofHours(1), 10, Duration.ofMinutes(1), Duration.ofSeconds(5), 2);
    }

    private StreamTrack track(String id, Instant expiresAfter) {
        return new StreamTrack(
                id,
                new TrackMetadata(Map.of()),
                null,
                TrackState.healthy(),
                URI.create("https://source/" + id),
                URI.create("https://stream"),
                expiresAfter
        );
    }
}
