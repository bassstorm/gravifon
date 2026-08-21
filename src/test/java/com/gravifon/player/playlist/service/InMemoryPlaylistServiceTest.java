package com.gravifon.player.playlist.service;

import com.gravifon.player.api.error.ResourceNotFoundException;
import com.gravifon.player.catalog.metadata.TrackDurationExtractor;
import com.gravifon.player.catalog.service.MediaCatalogService;
import com.gravifon.player.config.GravifonProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryPlaylistServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rebuildFromCatalog_createsDefaultPlaylistWithTracks() throws IOException {
        Files.createDirectories(tempDir.resolve("nested"));
        Files.createFile(tempDir.resolve("nested/one.mp3"));
        Files.createFile(tempDir.resolve("nested/two.ogg"));

        InMemoryPlaylistService service = createService(tempDir);
        service.rebuildFromCatalog();

        assertThat(service.listPlaylists()).singleElement().satisfies(playlist -> {
            assertThat(playlist.id()).isEqualTo(InMemoryPlaylistService.DEFAULT_PLAYLIST_ID);
            assertThat(playlist.trackIds()).hasSize(2);
        });
        assertThat(service.getActivePlaylist().id()).isEqualTo(InMemoryPlaylistService.DEFAULT_PLAYLIST_ID);
    }

    @Test
    void rebuildFromCatalog_keepsDefaultPlaylistWhenCatalogIsEmpty() {
        InMemoryPlaylistService service = createService(tempDir);
        service.rebuildFromCatalog();

        assertThat(service.listPlaylists()).singleElement().satisfies(playlist -> {
            assertThat(playlist.id()).isEqualTo(InMemoryPlaylistService.DEFAULT_PLAYLIST_ID);
            assertThat(playlist.trackIds()).isEmpty();
        });
    }

    @Test
    void selectPlaylist_throwsForUnknownPlaylist() {
        InMemoryPlaylistService service = createService(tempDir);
        service.rebuildFromCatalog();

        assertThatThrownBy(() -> service.selectPlaylist("missing"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("missing");
    }

    private InMemoryPlaylistService createService(Path root) {
        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(root);

        TrackDurationExtractor extractor = path -> OptionalLong.empty();
        MediaCatalogService mediaCatalogService = new MediaCatalogService(properties, extractor);
        mediaCatalogService.refreshCatalog();

        return new InMemoryPlaylistService(mediaCatalogService);
    }
}

