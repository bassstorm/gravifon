package com.gravifon.player.registry.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.gravifon.player.config.GravifonProperties;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.scan.LibraryScanner;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrackRegistryTest {

    @Test
    void refreshIndexesSupportedNestedFilesWithRelativeIdentity(@TempDir Path musicRoot) throws Exception {
        Path nestedTrack = musicRoot.resolve("albums/classic/track.mp3");
        Files.createDirectories(nestedTrack.getParent());
        Files.writeString(nestedTrack, "audio");
        Files.writeString(musicRoot.resolve("cover.jpg"), "ignored");

        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(musicRoot);
        LibraryScanner scanner = mock(LibraryScanner.class);
        when(scanner.scan(any())).thenReturn(List.of(
            new LibraryScanner.ScanFile("albums/classic/track.mp3", "mp3", true,
                Map.of(), 120L)));
        TrackRepository repository = mock(TrackRepository.class);
        List<Track> stored = new ArrayList<>();
        when(repository.findAll()).thenAnswer(ignored -> List.copyOf(stored));
        when(repository.findById(any())).thenAnswer(invocation -> stored.stream()
            .filter(track -> track.id().equals(invocation.getArgument(0))).findFirst());
        when(repository.save(any())).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            stored.removeIf(existing -> existing.id().equals(track.id()));
            stored.add(track);
            return track;
        });
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        when(playlists.findAll()).thenReturn(List.of());
        TrackRegistry registry = new TrackRegistry(properties, scanner, repository, playlists);

        registry.refresh();

        assertThat(registry.listTracks()).hasSize(1);
        assertThat(registry.listTracks().getFirst().relPath()).isEqualTo("albums/classic/track.mp3");
        assertThat(registry.listTracks().getFirst().durationSeconds()).isEqualTo(120L);
        assertThat(repository.findAll()).hasSize(1);
        assertThat(registry.resolveTrackPath(registry.listTracks().getFirst().id()))
                .contains(nestedTrack.toAbsolutePath().normalize());
    }

    @Test
    void unreadableFileIsRetainedAsReadErrorAndReadableRescanClearsIt(@TempDir Path musicRoot) {
        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(musicRoot);
        LibraryScanner scanner = mock(LibraryScanner.class);
        LibraryScanner.ScanFile unreadable = new LibraryScanner.ScanFile("track.mp3", "mp3", false, Map.of(), null);
        LibraryScanner.ScanFile readable = new LibraryScanner.ScanFile("track.mp3", "mp3", true, Map.of("TITLE", List.of("Ready")), 42L);
        AtomicInteger scanCount = new AtomicInteger();
        when(scanner.scan(any())).thenAnswer(invocation -> scanCount.getAndIncrement() == 0
            ? List.of(unreadable) : List.of(readable));
        TrackRepository repository = mock(TrackRepository.class);
        List<Track> stored = new ArrayList<>();
        when(repository.findAll()).thenAnswer(ignored -> List.copyOf(stored));
        when(repository.findById(any())).thenAnswer(invocation -> stored.stream()
                .filter(track -> track.id().equals(invocation.getArgument(0))).findFirst());
        when(repository.save(any())).thenAnswer(invocation -> {
            Track track = invocation.getArgument(0);
            stored.removeIf(existing -> existing.id().equals(track.id()));
            stored.add(track);
            return track;
        });
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        when(playlists.findAll()).thenReturn(List.of());
        TrackRegistry registry = new TrackRegistry(properties, scanner, repository, playlists);

        registry.refresh();
        assertThat(stored.getFirst().state().lastError().kind()).isEqualTo("READ_ERROR");

        registry.refresh();
        assertThat(stored.getFirst().state()).isEqualTo(TrackState.healthy());
        assertThat(stored.getFirst().metadata()).containsEntry("TITLE", List.of("Ready"));
    }

    @Test
    void missingReferencedTrackIsTombstonedAndUnreferencedTrackIsRemoved(@TempDir Path musicRoot) {
        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(musicRoot);
        LibraryScanner scanner = mock(LibraryScanner.class);
        when(scanner.scan(any())).thenReturn(List.of());
        TrackRepository repository = mock(TrackRepository.class);
        Track referenced = new Track("referenced", TrackKind.FILE, Map.of(), 1L, TrackState.healthy(), "kept.mp3", "mp3", null, null, null);
        Track unreferenced = new Track("unreferenced", TrackKind.FILE, Map.of(), 1L, TrackState.healthy(), "removed.mp3", "mp3", null, null, null);
        when(repository.findAll()).thenReturn(List.of(referenced, unreferenced));
        when(repository.findById("referenced")).thenReturn(Optional.of(referenced));
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        when(playlists.findAll()).thenReturn(List.of(new com.gravifon.player.playlist.model.Playlist(
                "playlist", "Mix", List.of("referenced"))));
        TrackRegistry registry = new TrackRegistry(properties, scanner, repository, playlists);

        registry.refresh();

        verify(repository).deleteById("unreferenced");
        verify(repository).save(any(Track.class));
    }
}