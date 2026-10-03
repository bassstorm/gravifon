package com.gravifon.player.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.repository.TrackRepository;
import com.gravifon.player.registry.service.StreamTrackRegistrar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PlaylistServiceTest {
    @Mock
    private PlaylistRepository playlists;
    @Mock
    private TrackRepository tracks;
    @Mock
    private StreamTrackRegistrar streamTrackRegistrar;

    @Test
    void freshStoreHasNoPlaylists() {
        when(playlists.findAll()).thenReturn(List.of());

        PlaylistService service = service();

        assertThat(service.listPlaylists()).isEmpty();
    }

    @Test
    void creationDefaultsToSequentialAndPreservesOrdering() {
        when(tracks.findAllById(List.of("b", "a"))).thenReturn(List.of(fileTrack("b"), fileTrack("a")));
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = service();

        Playlist playlist = service.create("Mix", List.of("b", "a"), null);

        assertThat(playlist.playbackMode()).isEqualTo(PlaybackMode.SEQUENTIAL);
        assertThat(playlist.trackIds()).containsExactly("b", "a");
    }

    @Test
    void unknownTrackRejectsCreationBeforePersistence() {
        when(tracks.findAllById(List.of("missing"))).thenReturn(List.of());
        PlaylistService service = service();

        assertThatThrownBy(() -> service.create("Mix", List.of("missing"), PlaybackMode.RANDOM))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sharedTrackCanBeAddedToMultiplePlaylists() {
        when(tracks.findAllById(List.of("shared"))).thenReturn(List.of(fileTrack("shared")));
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = service();

        Playlist first = service.create("First", List.of("shared"), null);
        Playlist second = service.create("Second", List.of("shared"), null);

        assertThat(first.trackIds()).containsExactly("shared");
        assertThat(second.trackIds()).containsExactly("shared");
        verify(tracks, times(2)).findAllById(List.of("shared"));
    }

    @Test
    void materializeCatalogCreatesAnOrdinarySnapshot() {
        when(tracks.findAll()).thenReturn(List.of(fileTrack("a"), fileTrack("b")));
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = service();

        Playlist playlist = service.materializeCatalog("Catalog snapshot");

        assertThat(playlist.name()).isEqualTo("Catalog snapshot");
        assertThat(playlist.trackIds()).containsExactly("a", "b");
        assertThat(playlist.playbackMode()).isEqualTo(PlaybackMode.SEQUENTIAL);
    }

    @Test
    void createFromRequestRegistersSourcesAndPersistsAllTracks() {
        when(streamTrackRegistrar.registerSources(List.of("https://example.com/album")))
            .thenReturn(List.of(fileTrack("stream")));
        when(tracks.findAllById(List.of("existing", "stream"))).thenReturn(List.of(
                fileTrack("existing"),
                fileTrack("stream")
        ));
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = service();

        Playlist playlist = service.createFromRequest(
                new PlaylistMutation(
                        "Mix",
                        List.of("existing"),
                        List.of("https://example.com/album"),
                        false,
                        List.of(),
                        List.of(),
                        List.of(),
                        PlaybackMode.RANDOM
                )
        );

        assertThat(playlist.trackIds()).containsExactly("existing", "stream");
        assertThat(playlist.playbackMode()).isEqualTo(PlaybackMode.RANDOM);
        verify(streamTrackRegistrar).registerSources(List.of("https://example.com/album"));
    }

    @Test
    void createFromRequestRejectsCatalogEntriesAtTheServiceBoundary() {
        PlaylistService service = service();

        assertThatThrownBy(() -> service.createFromRequest(
                new PlaylistMutation(
                        "Catalog",
                        List.of("track-1"),
                        List.of(),
                        true,
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                )
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Catalog materialization cannot include entries");
    }

    @Test
    void updateFromRequestAppliesAllMutationsBeforeSingleSave() {
        Playlist existing = new Playlist("p1", "Mix", List.of("a"), PlaybackMode.SEQUENTIAL);
        when(playlists.findById("p1")).thenReturn(Optional.of(existing));
        when(tracks.findAllById(List.of("a"))).thenReturn(List.of(fileTrack("a")));
        when(tracks.findAllById(List.of("b"))).thenReturn(List.of(fileTrack("b")));
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = service();

        Playlist updated = service.updateFromRequest(
                "p1",
                new PlaylistMutation(
                        "Renamed",
                        List.of(),
                        List.of(),
                        null,
                        List.of("a"),
                        List.of("b"),
                        List.of(),
                        PlaybackMode.RANDOM
                )
        );

        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.trackIds()).containsExactly("a", "b");
        assertThat(updated.playbackMode()).isEqualTo(PlaybackMode.RANDOM);
        verify(playlists).save(updated);
    }

    @Test
    void removingDuplicateEntryRemovesOnlyOneOccurrence() {
        when(playlists.findById("p1"))
            .thenReturn(Optional.of(new Playlist("p1", "Mix", List.of("a", "b", "a"), PlaybackMode.SEQUENTIAL)));
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = service();

        Playlist result = service.removeEntries("p1", List.of("a"));

        assertThat(result.trackIds()).containsExactly("b", "a");
    }

    private Track fileTrack(String id) {
        return new FileTrack(
                id,
                Map.of(),
                1L,
                com.gravifon.player.registry.model.TrackState.healthy(),
                id + ".mp3",
                "mp3"
        );
    }

    private PlaylistService service() {
        return new PlaylistService(playlists, tracks, streamTrackRegistrar);
    }
}
