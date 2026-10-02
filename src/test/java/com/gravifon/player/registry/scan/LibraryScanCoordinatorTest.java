package com.gravifon.player.registry.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import java.nio.file.Path;
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
        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(musicRoot);
        when(scanner.scan(any()))
            .thenReturn(List.of(new LibraryScanner.ScanFile("albums/classic/track.mp3", "mp3", true, Map.of(), 120L)));
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
                new LibraryScanCoordinator(properties, scanner, repository, referencedPort, new ScanReconciler());

        coordinator.scan();

        assertThat(stored).hasSize(1);
        assertThat(((FileTrack) stored.getFirst()).relPath()).isEqualTo("albums/classic/track.mp3");
        assertThat(stored.getFirst().durationSeconds()).isEqualTo(120L);
    }

    @Test
    void unreadableFileIsRetainedAsReadErrorAndReadableRescanClearsIt(@TempDir Path musicRoot) {
        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(musicRoot);
        LibraryScanner.ScanFile unreadable = new LibraryScanner.ScanFile("track.mp3", "mp3", false, Map.of(), null);
        LibraryScanner.ScanFile readable =
                new LibraryScanner.ScanFile("track.mp3", "mp3", true, Map.of("TITLE", List.of("Ready")), 42L);
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
                new LibraryScanCoordinator(properties, scanner, repository, referencedPort, new ScanReconciler());

        coordinator.scan();
        assertThat(stored.getFirst().state().lastError().kind()).isEqualTo("READ_ERROR");

        coordinator.scan();
        assertThat(stored.getFirst().state()).isEqualTo(TrackState.healthy());
        assertThat(stored.getFirst().metadata()).containsEntry("TITLE", List.of("Ready"));
    }

    @Test
    void missingReferencedTrackIsTombstonedAndUnreferencedTrackIsRemoved(@TempDir Path musicRoot) {
        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(musicRoot);
        when(scanner.scan(any())).thenReturn(List.of());
        Track referenced = new FileTrack("referenced", Map.of(), 1L, TrackState.healthy(), "kept.mp3", "mp3");
        Track unreferenced = new FileTrack("unreferenced", Map.of(), 1L, TrackState.healthy(), "removed.mp3", "mp3");
        when(repository.findAll()).thenReturn(List.of(referenced, unreferenced));
        when(repository.findById("referenced")).thenReturn(Optional.of(referenced));
        ReferencedTrackIdsPort referencedPort = () -> Set.of("referenced");
        LibraryScanCoordinator coordinator =
                new LibraryScanCoordinator(properties, scanner, repository, referencedPort, new ScanReconciler());

        coordinator.scan();

        verify(repository).deleteById("unreferenced");
        verify(repository).save(any(Track.class));
    }
}
