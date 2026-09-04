package com.gravifon.player.registry.scan;

import java.util.Set;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
@FunctionalInterface
public interface ReferencedTrackIdsPort {
    Set<String> getReferencedTrackIds();
}
