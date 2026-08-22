package com.gravifon.player;

import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playback.repository.PlaybackStateRepository;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.repository.PlaylistRepository;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.repository.TrackRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackRestartIntegrationTest {

    @Test
    void restartRestoresSavedPositionAndNormalizesPlayingToPaused(@TempDir Path tempDir) throws Exception {
        Path musicRoot = Files.createDirectories(tempDir.resolve("music"));
        Path configDir = Files.createDirectories(tempDir.resolve("config"));
        String[] properties = {
            "gravifon.music-root=" + musicRoot,
            "gravifon.config-dir=" + configDir,
            "gravifon.startup-scan-enabled=false",
            "gravifon.streams.refresh-enabled=false",
            "spring.main.web-application-type=none"
        };

        try (ConfigurableApplicationContext first = context(properties)) {
            TrackRepository tracks = first.getBean(TrackRepository.class);
            PlaylistRepository playlists = first.getBean(PlaylistRepository.class);
            PlaybackStateRepository states = first.getBean(PlaybackStateRepository.class);
            Track track = tracks.save(new Track("t1", TrackKind.FILE, Map.of(), 180L, TrackState.healthy(),
                    "track.mp3", "mp3", null, null, null));
            Playlist playlist = playlists.save(new Playlist("p1", "Playlist", List.of(track.id()), PlaybackMode.SEQUENTIAL));
            states.save("default", new PlaybackState(playlist.id(), track.id(), PlaybackMode.SEQUENTIAL,
                    TransportState.PLAYING, 42), "REPORTED");
        }

        try (ConfigurableApplicationContext restarted = context(properties)) {
            PlaybackState restored = restarted.getBean(PlaybackService.class).getState();
            assertThat(restored.activePlaylistId()).isEqualTo("p1");
            assertThat(restored.currentTrackId()).isEqualTo("t1");
            assertThat(restored.transportState()).isEqualTo(TransportState.PAUSED);
            assertThat(restored.positionSeconds()).isEqualTo(42);
        }
    }

    private ConfigurableApplicationContext context(String... properties) {
        SpringApplication application = new SpringApplication(GravifonApplication.class);
        String[] arguments = java.util.Arrays.stream(properties)
            .map(property -> "--" + property)
            .toArray(String[]::new);
        return application.run(arguments);
    }
}
