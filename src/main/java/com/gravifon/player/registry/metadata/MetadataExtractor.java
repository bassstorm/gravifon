package com.gravifon.player.registry.metadata;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.jmolecules.architecture.onion.simplified.DomainRing;

@DomainRing
public interface MetadataExtractor {
    ExtractedMetadata extract(Path path);

    record ExtractedMetadata(Map<String, List<String>> values, Long durationSeconds, boolean readable) {
        public ExtractedMetadata(Map<String, List<String>> values, Long durationSeconds) {
            this(values, durationSeconds, true);
        }
    }
}
