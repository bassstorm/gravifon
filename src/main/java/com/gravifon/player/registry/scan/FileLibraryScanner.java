package com.gravifon.player.registry.scan;

import com.gravifon.player.registry.metadata.MetadataExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.jmolecules.architecture.onion.simplified.InfrastructureRing;
import org.springframework.stereotype.Component;

@InfrastructureRing
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
            return paths.filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .map(path -> toScanFile(root, path))
                    .sorted(Comparator.comparing(ScanFile::relativePath))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to scan music root: " + root, exception);
        }
    }

    private ScanFile toScanFile(Path root, Path path) {
        String relativePath = root.relativize(path).toString().replace('\\', '/');
        try {
            MetadataExtractor.ExtractedMetadata metadata = metadataExtractor.extract(path);
            return new ScanFile(
                    relativePath, extension(path), metadata.readable(), metadata.values(), metadata.durationSeconds());
        } catch (RuntimeException exception) {
            log.warn("Unable to read file {} during scan: {}", path, exception.toString());
            return new ScanFile(relativePath, extension(path), false, java.util.Map.of(), null);
        }
    }

    private boolean isSupported(Path path) {
        return SUPPORTED_EXTENSIONS.contains(extension(path));
    }

    private String extension(Path path) {
        String filename = path.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        return dot > 0 && dot < filename.length() - 1
                ? filename.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
    }
}
