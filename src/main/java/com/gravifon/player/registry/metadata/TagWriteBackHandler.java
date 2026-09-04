package com.gravifon.player.registry.metadata;

import com.gravifon.player.registry.model.Track;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public interface TagWriteBackHandler {
    boolean supports(Track track);
    WriteBackResult writeBack(Track track, Map<String, List<String>> metadata);

    record WriteBackResult(boolean accepted, String message) {
    }
}