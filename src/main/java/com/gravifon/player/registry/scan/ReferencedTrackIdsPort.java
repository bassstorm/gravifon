package com.gravifon.player.registry.scan;

import java.util.Set;

@FunctionalInterface
public interface ReferencedTrackIdsPort {
    Set<String> getReferencedTrackIds();
}
