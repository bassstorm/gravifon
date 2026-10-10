package com.gravifon.player.registry.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.TrackState;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RefreshPlannerTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration AHEAD = Duration.ofMinutes(10);
    private final RefreshPlanner planner = new RefreshPlanner();

    @Test
    void plansTracksWithinOrAtLookAheadBoundaryAndIgnoresNonStreamTracks() {
        StreamTrack notDue = streamTrack("not-due", "https://stream", NOW.plus(AHEAD).plusSeconds(1));
        StreamTrack insideWindow = streamTrack("inside", "https://stream", NOW.plus(AHEAD).minusSeconds(1));
        StreamTrack boundary = streamTrack("boundary", "https://stream", NOW.plus(AHEAD));
        StreamTrack expired = streamTrack("expired", "https://stream", NOW.minusSeconds(1));
        FileTrack fileTrack = new FileTrack("file", Map.of(), null, TrackState.healthy(), "file.mp3", "mp3");

        List<StreamTrack> due = planner.plan(List.of(notDue, insideWindow, boundary, expired, fileTrack), NOW, AHEAD);

        assertThat(due).extracting(StreamTrack::id).containsExactly("expired", "inside", "boundary");
    }

    @Test
    void plansMissingUrlFirstAndThenByEarliestExpiry() {
        StreamTrack missingUrl = streamTrack("missing", null, null);
        StreamTrack laterExpiry = streamTrack("later", "https://stream/later", NOW.plusSeconds(90));
        StreamTrack earlierExpiry = streamTrack("earlier", "https://stream/earlier", NOW.plusSeconds(30));
        StreamTrack nonExpiring = streamTrack("non-expiring", "https://stream/non-expiring", null);

        List<StreamTrack> due = planner.plan(List.of(laterExpiry, nonExpiring, missingUrl, earlierExpiry), NOW, AHEAD);

        assertThat(due).extracting(StreamTrack::id).containsExactly("missing", "earlier", "later");
    }

    private StreamTrack streamTrack(String id, String streamUrl, Instant expiresAfter) {
        return new StreamTrack(
                id,
                Map.of(),
                null,
                TrackState.healthy(),
                "https://source/" + id,
                streamUrl,
                expiresAfter
        );
    }
}
