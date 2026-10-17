package com.gravifon.player.registry.scan;

import com.gravifon.player.registry.model.TrackMetadata;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public interface LibraryScanner {
    List<ScanFile> scan(Path root);

    record ScanFile(
            Path relativePath,
            Instant modifiedTime,
            TrackMetadata metadata,
            Long durationSeconds,
            String format,
            boolean readable
    ) {
        public ScanFile(Path relativePath, Instant modifiedTime, TrackMetadata metadata, Long durationSeconds) {
            this(relativePath, modifiedTime, metadata, durationSeconds, extension(relativePath), true);
        }

        private static String extension(Path path) {
            String filename = path.getFileName().toString();
            int dot = filename.lastIndexOf('.');
            return dot > 0 && dot < filename.length() - 1
                    ? filename.substring(dot + 1).toLowerCase(java.util.Locale.ROOT)
                    : "";
        }
    }
}
