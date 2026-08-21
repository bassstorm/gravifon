package com.gravifon.player.catalog.model;

import java.nio.file.Path;

public record Track(
        String id,
        Path sourcePath,
        String filename,
        String format,
        Long durationSeconds
) {
}

