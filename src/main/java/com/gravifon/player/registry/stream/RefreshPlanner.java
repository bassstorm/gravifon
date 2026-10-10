package com.gravifon.player.registry.stream;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.jmolecules.architecture.onion.simplified.ApplicationRing;
import org.springframework.stereotype.Component;

@ApplicationRing
@Component
public class RefreshPlanner {
    private static final Comparator<StreamTrack> URGENCY_ORDER = Comparator
        .comparing((StreamTrack track) -> track.streamUrl() != null)
        .thenComparing(StreamTrack::expiresAfter, Comparator.nullsLast(Comparator.naturalOrder()));

    public List<StreamTrack> plan(Collection<Track> upcoming, Instant now, Duration ahead) {
        Objects.requireNonNull(upcoming, "upcoming");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(ahead, "ahead");
        if (ahead.isNegative()) {
            throw new IllegalArgumentException("Refresh look-ahead must not be negative");
        }
        return upcoming
            .stream()
            .filter(StreamTrack.class::isInstance)
            .map(StreamTrack.class::cast)
            .filter(track -> track.needsRefresh(now, ahead))
            .sorted(URGENCY_ORDER)
            .toList();
    }
}
