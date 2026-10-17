package com.gravifon.player.registry.scan;

import java.util.Set;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.stereotype.Component;

@InfrastructureRing
@Component
public class NoDurableTrackReferencesAdapter implements ReferencedTrackIdsPort {
    @Override
    public Set<String> getReferencedTrackIds() {
        return Set.of();
    }
}
