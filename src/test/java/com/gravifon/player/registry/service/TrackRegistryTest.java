package com.gravifon.player.registry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.scan.LibraryScanCoordinator;
import java.nio.file.Path;
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

        assertThat(registry.resolveTrackPath("t1"))
                .contains(musicRoot.resolve("song.mp3").normalize());
    }
}
