package com.gravifon.player.registry.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.LibraryProperties;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class LibraryScanCoordinatorTest {
    @Mock
    private LibraryScanner scanner;
    @Mock
    private TrackRepository repository;

    @Test
    void scanIndexesSupportedNestedFilesWithRelativeIdentity(@TempDir Path musicRoot) {
        LibraryProperties properties = new LibraryProperties(musicRoot, true);
        when(scanner.scan(any())).thenReturn(List.of(scanFile("albums/classic/track.mp3", "mp3", true, Map.of(), 120L)));
        List<Track> stored = new ArrayList<>();
        when(repository.findAll()).thenAnswer(ignored -> List.copyOf(stored));
        when(repository.save(any())).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            stored.removeIf(existing -> existing.id().equals(track.id()));
            stored.add(track);
            return track;
        });
        ReferencedTrackIdsPort referencedPort = () -> Set.of();
        LibraryScanCoordinator coordinator =
                new LibraryScanCoordinator(
                        properties,
                        scanner,
                        repository,
                        List.of(referencedPort),
                        new ScanReconciler()
        );

        coordinator.scan();

        assertThat(stored).hasSize(1);
        assertThat(((FileTrack) stored.getFirst()).relPath()).isEqualTo(Path.of("albums/classic/track.mp3"));
        assertThat(stored.getFirst().durationSeconds()).isEqualTo(120L);
    }

    @Test
    void unreadableFileIsRetainedAsReadErrorAndReadableRescanClearsIt(@TempDir Path musicRoot) {
        LibraryProperties properties = new LibraryProperties(musicRoot, true);
        LibraryScanner.ScanFile unreadable = scanFile("track.mp3", "mp3", false, Map.of(), null);
        LibraryScanner.ScanFile readable = scanFile("track.mp3", "mp3", true, Map.of("TITLE", List.of("Ready")), 42L);
        AtomicInteger scanCount = new AtomicInteger();
        when(scanner.scan(any()))
            .thenAnswer(invocation -> scanCount.getAndIncrement() == 0 ? List.of(unreadable) : List.of(readable));
        List<Track> stored = new ArrayList<>();
        when(repository.findAll()).thenAnswer(ignored -> List.copyOf(stored));
        when(repository.save(any())).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            stored.removeIf(existing -> existing.id().equals(track.id()));
            stored.add(track);
            return track;
        });
        ReferencedTrackIdsPort referencedPort = () -> Set.of();
        LibraryScanCoordinator coordinator =
                new LibraryScanCoordinator(
                        properties,
                        scanner,
                        repository,
                        List.of(referencedPort),
                        new ScanReconciler()
        );

        coordinator.scan();
        assertThat(stored.getFirst().state().lastError().kind()).isEqualTo("READ_ERROR");

        coordinator.scan();
        assertThat(stored.getFirst().state()).isEqualTo(TrackState.healthy());
        assertThat(stored.getFirst().metadata().values()).containsEntry("TITLE", List.of("Ready"));
    }

    @Test
    void missingReferencedTrackIsTombstonedAndUnreferencedTrackIsRemoved(@TempDir Path musicRoot) {
        LibraryProperties properties = new LibraryProperties(musicRoot, true);
        when(scanner.scan(any())).thenReturn(List.of());
        Track referenced = new FileTrack(
                "referenced",
                new TrackMetadata(Map.of()),
                1L,
                TrackState.healthy(),
                Path.of("kept.mp3"),
                "mp3"
        );
        Track unreferenced = new FileTrack(
                "unreferenced",
                new TrackMetadata(Map.of()),
                1L,
                TrackState.healthy(),
                Path.of("removed.mp3"),
                "mp3"
        );
        when(repository.findAll()).thenReturn(List.of(referenced, unreferenced));
        when(repository.findById("referenced")).thenReturn(Optional.of(referenced));
        ReferencedTrackIdsPort referencedPort = () -> Set.of("referenced");
        LibraryScanCoordinator coordinator =
                new LibraryScanCoordinator(
                        properties,
                        scanner,
                        repository,
                        List.of(referencedPort),
                        new ScanReconciler()
        );

        coordinator.scan();

        verify(repository).deleteById("unreferenced");
        verify(repository).save(any(Track.class));
    }

    @Test
    void scanUnionsReferencesFromMultipleProvidersAndAcceptsNoProviders(@TempDir Path musicRoot) {
        LibraryProperties properties = new LibraryProperties(musicRoot, true);
        when(scanner.scan(any())).thenReturn(List.of());
        Track first =
                new FileTrack(
                        "first",
                        new TrackMetadata(Map.of()),
                        1L,
                        TrackState.healthy(),
                        Path.of("first.mp3"),
                        "mp3"
        );
        Track second =
                new FileTrack(
                        "second",
                        new TrackMetadata(Map.of()),
                        1L,
                        TrackState.healthy(),
                        Path.of("second.mp3"),
                        "mp3"
        );
        Track unreferenced = new FileTrack(
                "unreferenced",
                new TrackMetadata(Map.of()),
                1L,
                TrackState.healthy(),
                Path.of("removed.mp3"),
                "mp3"
        );
        when(repository.findAll()).thenReturn(List.of(first, second, unreferenced));
        when(repository.findById("first")).thenReturn(Optional.of(first));
        when(repository.findById("second")).thenReturn(Optional.of(second));

        LibraryScanCoordinator coordinator = new LibraryScanCoordinator(
                properties,
                scanner,
                repository,
                List.of(() -> Set.of("first"), () -> Set.of("second", "first")),
                new ScanReconciler()
        );

        coordinator.scan();

        verify(repository, times(2)).save(any(Track.class));
        verify(repository).deleteById("unreferenced");

        LibraryScanCoordinator withoutProviders =
                new LibraryScanCoordinator(properties, scanner, repository, List.of(), new ScanReconciler());
        withoutProviders.scan();

        verify(repository).deleteById("first");
        verify(repository).deleteById("second");
    }

    private static LibraryScanner.ScanFile scanFile(
            String path,
            String format,
            boolean readable,
            Map<String, List<String>> metadata,
            Long durationSeconds
    ) {
        return new LibraryScanner.ScanFile(
                Path.of(path),
                Instant.EPOCH,
                new TrackMetadata(metadata),
                durationSeconds,
                format,
                readable
        );
    }
}
