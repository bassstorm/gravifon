package com.gravifon.player.registry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.error.EntityNotFoundException;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
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
    @Test
    void delegatesRefreshToScanCoordinator() {
        GravifonProperties properties = new GravifonProperties();
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        TrackRegistry registry = new TrackRegistry(properties, repository, coordinator, List.of());

        registry.refresh();

        verify(coordinator).scan();
    }

    @Test
    void resolveTrackPath_returnsPathForFileTrack(@TempDir Path musicRoot) {
        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(musicRoot);
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track = new FileTrack("t1", Map.of(), 100L, TrackState.healthy(), "song.mp3", "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));

        TrackRegistry registry = new TrackRegistry(properties, repository, coordinator, List.of());

        assertThat(registry.resolveTrackPath("t1")).isEqualTo(musicRoot.resolve("song.mp3").normalize());
    }

    @Test
    void getTrack_returnsTrackAndThrowsWhenMissing() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track = new FileTrack("t1", Map.of(), 100L, TrackState.healthy(), "song.mp3", "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));
        TrackRegistry registry = new TrackRegistry(new GravifonProperties(), repository, coordinator, List.of());

        assertThat(registry.getTrack("t1")).isEqualTo(track);
        assertThatThrownBy(() -> registry.getTrack("missing")).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void resolveTrackPath_throwsWhenTrackHasNoFilePath() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track streamTrack =
                new StreamTrack("t1", Map.of(), null, TrackState.healthy(), "https://example.com", null, null);
        when(repository.findById("t1")).thenReturn(Optional.of(streamTrack));
        TrackRegistry registry = new TrackRegistry(new GravifonProperties(), repository, coordinator, List.of());

        assertThatThrownBy(() -> registry.resolveTrackPath("t1"))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Track path not found: t1");
    }

    @Test
    void registryMutationsAndPathResolution_throwWhenTrackIsMissing() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        TrackRegistry registry = new TrackRegistry(new GravifonProperties(), repository, coordinator, List.of());

        assertThatThrownBy(() -> registry.updateMetadata("missing", Map.of())).isInstanceOf(
                EntityNotFoundException.class
        );
        assertThatThrownBy(() -> registry.reportFailure("missing", "CLIENT_ERROR", "failed"))
            .isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> registry.resolveTrackPath("missing")).isInstanceOf(EntityNotFoundException.class);
        assertThatThrownBy(() -> registry.markStreamUnreachable("missing", "failed"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void reportFailureAndClearErrorAreExplicitAndRejectInvalidReports() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track = new FileTrack("t1", Map.of(), 100L, TrackState.healthy(), "song.mp3", "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));
        when(repository.save(org.mockito.ArgumentMatchers.any(Track.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        TrackRegistry registry = new TrackRegistry(new GravifonProperties(), repository, coordinator, List.of());

        assertThat(registry.reportFailure("t1", "UPSTREAM", "Unavailable").state().failing()).isTrue();
        assertThat(registry.clearError("t1").state()).isEqualTo(TrackState.healthy());
        assertThatThrownBy(() -> registry.reportFailure("t1", "UPSTREAM", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-blank");
    }

    @Test
    void updateMetadataRejectsMalformedValuesInsteadOfThrowingNullPointerException() {
        TrackRepository repository = mock(TrackRepository.class);
        LibraryScanCoordinator coordinator = mock(LibraryScanCoordinator.class);
        Track track = new FileTrack("t1", Map.of(), 100L, TrackState.healthy(), "song.mp3", "mp3");
        when(repository.findById("t1")).thenReturn(Optional.of(track));
        TrackRegistry registry = new TrackRegistry(new GravifonProperties(), repository, coordinator, List.of());

        assertThatThrownBy(() -> registry.updateMetadata("t1", Map.of("TITLE", java.util.Arrays.asList("valid", null))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");

        Map<String, List<String>> nullValues = new HashMap<>();
        nullValues.put("TITLE", null);
        assertThatThrownBy(() -> registry.updateMetadata("t1", nullValues))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be empty");
    }
}
