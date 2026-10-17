package com.gravifon.player.registry.scan;

import com.gravifon.player.registry.metadata.MetadataExtractor;
import com.gravifon.player.registry.model.TrackMetadata;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FileLibraryScanner implements LibraryScanner {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("flac", "mp3", "ogg");
    private final MetadataExtractor metadataExtractor;

    public FileLibraryScanner(MetadataExtractor metadataExtractor) {
        this.metadataExtractor = metadataExtractor;
    }

    @Override
    public List<ScanFile> scan(Path root) {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                .filter(Files::isRegularFile)
                .filter(this::isSupported)
                .map(path -> toScanFile(root, path))
                .sorted(Comparator.comparing(ScanFile::relativePath))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan music root: " + root, exception);
        }
    }

    private ScanFile toScanFile(Path root, Path path) {
        Path relativePath = root.relativize(path);
        Instant modifiedTime;
        try {
            modifiedTime = Files.readAttributes(path, BasicFileAttributes.class).lastModifiedTime().toInstant();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read file modification time: " + path, exception);
        }
        try {
            MetadataExtractor.ExtractedMetadata metadata = metadataExtractor.extract(path);
            return new ScanFile(
                    relativePath,
                    modifiedTime,
                    new TrackMetadata(metadata.values()),
                    metadata.durationSeconds(),
                    extension(path),
                    metadata.readable()
            );
        } catch (RuntimeException exception) {
            log.warn("Unable to read file {} during scan: {}", path, exception.toString());
            return new ScanFile(
                    relativePath,
                    modifiedTime,
                    new TrackMetadata(java.util.Map.of()),
                    null,
                    extension(path),
                    false
            );
        }
    }

    private boolean isSupported(Path path) {
        return SUPPORTED_EXTENSIONS.contains(extension(path));
    }

    private String extension(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot > 0 && dot < filename.length() - 1 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
