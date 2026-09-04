package com.gravifon.player.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.repository.TrackRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaylistServiceTest {

    @Test
    void freshStoreHasNoPlaylists() {
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        when(playlists.findAll()).thenReturn(List.of());

        PlaylistService service = new PlaylistService(playlists, mock(TrackRepository.class));

        assertThat(service.listPlaylists()).isEmpty();
    }

    @Test
    void creationDefaultsToSequentialAndPreservesOrdering() {
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        TrackRepository tracks = tracks("a", "b");
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = new PlaylistService(playlists, tracks);

        Playlist playlist = service.create("Mix", List.of("b", "a"), null);

        assertThat(playlist.playbackMode()).isEqualTo(PlaybackMode.SEQUENTIAL);
        assertThat(playlist.trackIds()).containsExactly("b", "a");
    }

    @Test
    void unknownTrackRejectsCreationBeforePersistence() {
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        TrackRepository tracks = tracks("known");
        PlaylistService service = new PlaylistService(playlists, tracks);

        assertThatThrownBy(() -> service.create("Mix", List.of("missing"), PlaybackMode.RANDOM))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sharedTrackReferenceIsVisibleInEveryPlaylistAfterRegistryUpdate() {
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        TrackRepository tracks = tracks("shared");
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = new PlaylistService(playlists, tracks);

        Playlist first = service.create("First", List.of("shared"), null);
        Playlist second = service.create("Second", List.of("shared"), null);

        assertThat(first.trackIds()).containsExactly("shared");
        assertThat(second.trackIds()).containsExactly("shared");
        assertThat(tracks.findById("shared"))
                .containsSame(tracks.findById("shared").orElseThrow());
    }

    @Test
    void materializeCatalogCreatesAnOrdinarySnapshot() {
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        TrackRepository tracks = tracks("a", "b");
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = new PlaylistService(playlists, tracks);

        Playlist playlist = service.materializeCatalog("Catalog snapshot");

        assertThat(playlist.name()).isEqualTo("Catalog snapshot");
        assertThat(playlist.trackIds()).containsExactly("a", "b");
        assertThat(playlist.playbackMode()).isEqualTo(PlaybackMode.SEQUENTIAL);
    }

    @Test
    void removingDuplicateEntryRemovesOnlyOneOccurrence() {
        PlaylistRepository playlists = mock(PlaylistRepository.class);
        when(playlists.findById("p1"))
                .thenReturn(Optional.of(new Playlist("p1", "Mix", List.of("a", "b", "a"), PlaybackMode.SEQUENTIAL)));
        when(playlists.save(any(Playlist.class))).thenAnswer(invocation -> invocation.getArgument(0));
        PlaylistService service = new PlaylistService(playlists, tracks("a", "b"));

        Playlist result = service.removeEntries("p1", List.of("a"));

        assertThat(result.trackIds()).containsExactly("b", "a");
    }

    private TrackRepository tracks(String... ids) {
        TrackRepository repository = mock(TrackRepository.class);
        List<Track> storedTracks = new ArrayList<>();
        for (String id : ids) {
            Track track = new FileTrack(
                    id, Map.of(), 1L, com.gravifon.player.registry.model.TrackState.healthy(), id + ".mp3", "mp3");
            storedTracks.add(track);
            when(repository.findById(id)).thenReturn(Optional.of(track));
        }
        when(repository.findAll()).thenReturn(storedTracks);
        when(repository.findAllById(any(Iterable.class))).thenAnswer(invocation -> {
            Iterable<String> requestedIds = invocation.getArgument(0);
            List<String> idList = new ArrayList<>();
            requestedIds.forEach(idList::add);
            return storedTracks.stream().filter(t -> idList.contains(t.id())).toList();
        });
        return repository;
    }
}
