package com.gravifon.player.catalog.service;

import com.gravifon.player.catalog.metadata.TrackDurationExtractor;
import com.gravifon.player.catalog.model.Track;
import com.gravifon.player.config.GravifonProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class MediaCatalogService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("flac", "mp3", "ogg");

    private final GravifonProperties properties;
    private final TrackDurationExtractor durationExtractor;
    private final AtomicReference<List<Track>> tracksRef = new AtomicReference<>(List.of());
    private final AtomicReference<Map<String, Track>> tracksByIdRef = new AtomicReference<>(Map.of());

    public MediaCatalogService(GravifonProperties properties, TrackDurationExtractor durationExtractor) {
        this.properties = properties;
        this.durationExtractor = durationExtractor;
    }

    @PostConstruct
    public void scanOnStartup() {
        if (!properties.isStartupScanEnabled()) {
            log.info("Startup scan disabled by configuration.");
            return;
        }
        refreshCatalog();
    }

    public synchronized void refreshCatalog() {
        Path root = properties.getMusicRoot().toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            log.warn("Music root {} does not exist. Catalog will be empty.", root);
            tracksRef.set(List.of());
            tracksByIdRef.set(Map.of());
            return;
        }

        try (Stream<Path> walk = Files.walk(root)) {
            List<Track> tracks = walk
                    .filter(Files::isRegularFile)
                    .filter(this::isSupported)
                    .map(path -> toTrack(root, path))
                    .sorted(Comparator.comparing(track -> track.sourcePath().toString()))
                    .toList();

            Map<String, Track> byId = tracks.stream().collect(Collectors.toUnmodifiableMap(Track::id, Function.identity()));
            tracksRef.set(tracks);
            tracksByIdRef.set(byId);
            log.info("Catalog scan complete. Indexed {} tracks from {}.", tracks.size(), root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan music root: " + root, e);
        }
    }

    public List<Track> listTracks() {
        return tracksRef.get();
    }

    public Optional<Track> findTrackById(String trackId) {
        return Optional.ofNullable(tracksByIdRef.get().get(trackId));
    }

    public Optional<Path> resolveTrackPath(String trackId) {
        return findTrackById(trackId).map(Track::sourcePath);
    }

    private boolean isSupported(Path path) {
        String filename = path.getFileName().toString();
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            return false;
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    private Track toTrack(Path root, Path path) {
        String filename = path.getFileName().toString();
        String format = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        var duration = durationExtractor.extractDurationSeconds(path);
        Long durationSeconds = duration.isPresent() ? duration.getAsLong() : null;
        return new Track(trackId(root, path), path.toAbsolutePath().normalize(), filename, format, durationSeconds);
    }

    private String trackId(Path root, Path path) {
        Path normalizedPath = path.toAbsolutePath().normalize();
        Path relative = root.relativize(normalizedPath);
        byte[] digest = sha1(relative.toString());
        return HexFormat.of().formatHex(digest);
    }

    private byte[] sha1(String value) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
            return messageDigest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 algorithm unavailable", e);
        }
    }
}


