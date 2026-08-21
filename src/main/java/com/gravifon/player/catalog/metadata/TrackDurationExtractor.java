package com.gravifon.player.catalog.metadata;

import java.nio.file.Path;
import java.util.OptionalLong;

public interface TrackDurationExtractor {

    OptionalLong extractDurationSeconds(Path path);
}

