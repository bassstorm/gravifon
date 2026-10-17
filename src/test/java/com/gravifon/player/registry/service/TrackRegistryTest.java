package com.gravifon.player.registry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.LibraryProperties;
import com.gravifon.player.error.EntityNotFoundException;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.scan.LibraryScanCoordinator;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackRegistryTest {
    private static final LibraryProperties DEFAULT_PROPERTIES = new LibraryProperties(Path.of("/music"), true);

    @Test
    void delegatesRefreshToScanCoordinator() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        TrackRegistry registry = new TrackRegistry(DEFAULT_PROPERTIES, repository, coordinator, List.of());

        registry.refresh();

        verify(coordinator).scan();
    }

    @Test
    void resolveTrackPath_returnsPathForFileTrack(@TempDir Path musicRoot) {
        LibraryProperties properties = new LibraryProperties(musicRoot, true);
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track =
                new FileTrack("t1", new TrackMetadata(Map.of()), 100L, TrackState.healthy(), Path.of("song.mp3"), "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));

        TrackRegistry registry = new TrackRegistry(properties, repository, coordinator, List.of());

        assertThat(registry.resolveTrackPath((FileTrack) track)).isEqualTo(musicRoot.resolve("song.mp3").normalize());
    }

    @Test
    void getTrack_returnsTrackAndThrowsWhenMissing() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track =
                new FileTrack("t1", new TrackMetadata(Map.of()), 100L, TrackState.healthy(), Path.of("song.mp3"), "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));
        TrackRegistry registry = new TrackRegistry(DEFAULT_PROPERTIES, repository, coordinator, List.of());

        assertThat(registry.getTrack("t1")).isEqualTo(track);
        assertThatThrownBy(() -> registry.getTrack("missing")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void registryMutationsAndPathResolution_throwWhenTrackIsMissing() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        TrackRegistry registry = new TrackRegistry(DEFAULT_PROPERTIES, repository, coordinator, List.of());

        assertThatThrownBy(() -> registry.updateMetadata("missing", new TrackMetadata(Map.of())))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> registry.reportFailure("missing", "CLIENT_ERROR", "failed"))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> registry.markStreamUnreachable("missing", "failed"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void reportFailureAndClearErrorAreExplicitAndRejectInvalidReports() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track =
                new FileTrack("t1", new TrackMetadata(Map.of()), 100L, TrackState.healthy(), Path.of("song.mp3"), "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));
        when(repository.save(org.mockito.ArgumentMatchers.any(Track.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        TrackRegistry registry = new TrackRegistry(DEFAULT_PROPERTIES, repository, coordinator, List.of());

        assertThat(registry.reportFailure("t1", "UPSTREAM", "Unavailable").state().failing()).isTrue();
        assertThat(registry.clearError("t1").state()).isEqualTo(TrackState.healthy());
        assertThatThrownBy(() -> registry.reportFailure("t1", "UPSTREAM", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Track error message must not be blank");
    }

    @Test
    void updateMetadataRejectsMalformedValuesInsteadOfThrowingNullPointerException() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track =
                new FileTrack("t1", new TrackMetadata(Map.of()), 100L, TrackState.healthy(), Path.of("song.mp3"), "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));
        TrackRegistry registry = new TrackRegistry(DEFAULT_PROPERTIES, repository, coordinator, List.of());

        assertThatThrownBy(() -> registry.updateMetadata(
                "t1",
                new TrackMetadata(Map.of("TITLE", java.util.Arrays.asList("valid", null)))
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");

        Map<String, List<String>> nullValues = new HashMap<>();
        nullValues.put("TITLE", null);
        assertThatThrownBy(() -> registry.updateMetadata("t1", new TrackMetadata(nullValues)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be empty");
    }
}
