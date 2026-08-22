package com.gravifon.player.registry.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public interface LibraryScanner {

    List<ScanFile> scan(Path root);

    record ScanFile(String relativePath, String format, boolean readable, Map<String, List<String>> metadata,
                    Long durationSeconds) {

        public ScanFile(String relativePath, String format, boolean readable, Long durationSeconds) {
            this(relativePath, format, readable, Map.of(), durationSeconds);
        }
    }
}