package com.gravifon.player.catalog.service;

import com.gravifon.player.catalog.metadata.TrackDurationExtractor;
import com.gravifon.player.catalog.model.Track;
import com.gravifon.player.config.GravifonProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MediaCatalogServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void refreshCatalog_scansRecursivelyAndFiltersUnsupportedExtensions() throws IOException {
        Path nested = Files.createDirectories(tempDir.resolve("nested/album"));
        Path mp3 = Files.createFile(nested.resolve("song-1.mp3"));
        Path flac = Files.createFile(tempDir.resolve("song-2.flac"));
        Files.createFile(tempDir.resolve("notes.txt"));

        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(tempDir);

        TrackDurationExtractor durationExtractor = path -> OptionalLong.of(120);
        MediaCatalogService service = new MediaCatalogService(properties, durationExtractor);

        service.refreshCatalog();

        List<Track> tracks = service.listTracks();
        assertThat(tracks).hasSize(2);
        assertThat(tracks)
                .extracting(Track::sourcePath)
                .containsExactlyInAnyOrder(mp3.toAbsolutePath().normalize(), flac.toAbsolutePath().normalize());
        assertThat(tracks).extracting(Track::format).containsExactlyInAnyOrder("mp3", "flac");
    }

    @Test
    void refreshCatalog_keepsTrackWhenDurationExtractionFails() throws IOException {
        Path ogg = Files.createFile(tempDir.resolve("ambient.ogg"));

        GravifonProperties properties = new GravifonProperties();
        properties.setMusicRoot(tempDir);

        TrackDurationExtractor durationExtractor = path -> OptionalLong.empty();
        MediaCatalogService service = new MediaCatalogService(properties, durationExtractor);

        service.refreshCatalog();

        List<Track> tracks = service.listTracks();
        assertThat(tracks).singleElement().satisfies(track -> {
            assertThat(track.sourcePath()).isEqualTo(ogg.toAbsolutePath().normalize());
            assertThat(track.durationSeconds()).isNull();
            assertThat(service.findTrackById(track.id())).contains(track);
            assertThat(service.resolveTrackPath(track.id())).contains(ogg.toAbsolutePath().normalize());
        });
    }
}

