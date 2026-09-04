package com.gravifon.player;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "gravifon.startup-scan-enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GravifonIntegrationTest {
    @TempDir
    static Path musicRoot;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("gravifon.music-root", () -> musicRoot.toString());
    }

    @BeforeAll
    static void createMusicFiles() throws IOException {
        Files.createDirectories(musicRoot.resolve("albums/classic"));
        Files.writeString(musicRoot.resolve("albums/classic/track-a.mp3"), "FAKE_AUDIO_BYTES_A");
        Files.writeString(musicRoot.resolve("albums/classic/track-b.ogg"), "FAKE_AUDIO_BYTES_B");
        Files.writeString(musicRoot.resolve("readme.txt"), "ignored");
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TrackRegistry trackRegistry;
    @Autowired
    private PlaylistService playlistService;
    private String playlistId;

    @BeforeEach
    void refreshCatalogAndPlaylists() {
        trackRegistry.refresh();
        if (playlistService.listPlaylists().isEmpty()) {
            playlistId = playlistService.materializeCatalog("Integration Playlist").id();
        } else {
            playlistId = playlistService.listPlaylists().getFirst().id();
        }
        playlistService.select(playlistId);
    }

    // ── Track endpoints ──────────────────────────────────────────────────
    @Test
    void tracks_listIncludesBothAudioFiles() throws Exception {
        mockMvc
            .perform(get("/api/tracks")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "it-cid-001")
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "it-cid-001"))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id", notNullValue()))
            .andExpect(jsonPath("$[0].format").isNotEmpty());
    }

    @Test
    void tracks_unknownTrackReturns404() throws Exception {
        mockMvc
            .perform(get("/api/tracks/nonexistent").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    // ── Playlist endpoints ───────────────────────────────────────────────
    @Test
    void playlists_materializedRegistryPlaylistExistsWithTwoTracks() throws Exception {
        mockMvc
            .perform(get("/api/playlists").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
            .andExpect(jsonPath("$[0].id").value(playlistId))
            .andExpect(jsonPath("$[0].trackIds", hasSize(2)))
            .andExpect(jsonPath("$[0].active").value(true));
    }

    @Test
    void playlists_selectAndReturnPlaybackSnapshot() throws Exception {
        mockMvc
            .perform(post("/api/playlists/" + playlistId + "/select").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activePlaylistId").value(playlistId))
            .andExpect(jsonPath("$.currentTrackId", notNullValue()));
    }

    @Test
    void playlists_createEndpointPersistsPlaylist() throws Exception {
        mockMvc
            .perform(post("/api/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Created Playlist\",\"catalog\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Created Playlist"))
            .andExpect(jsonPath("$.trackIds", hasSize(2)));
    }

    // ── Playback control flow ────────────────────────────────────────────
    @Test
    void playback_stateFlowWithServerContextVerification() throws Exception {
        String trackId = trackRegistry.listTracks().getFirst().id();
        // Prepare a track and explicitly report the server-owned transport state.
        mockMvc
            .perform(post("/api/playback/track/" + trackId)
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "it-cid-play")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTrackId").value(trackId))
            .andExpect(jsonPath("$.transportState").value("stopped"));

        mockMvc
            .perform(post("/api/playback/transport/playing"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transportState").value("playing"));

        mockMvc
            .perform(post("/api/playback/position")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"trackId\":\"" + trackId + "\",\"positionSeconds\":0}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.positionSeconds").value(0));

        mockMvc
            .perform(post("/api/playback/next"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.currentTrackId", notNullValue()));
        // Switch to random mode
        mockMvc
            .perform(post("/api/playback/mode/random"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.playbackMode").value("random"));

        mockMvc
            .perform(post("/api/playback/transport/stopped"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transportState").value("stopped"))
            .andExpect(jsonPath("$.positionSeconds").value(0));
        // A reconnect reads the same global snapshot without a server session.
        mockMvc
            .perform(get("/api/playback").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.transportState").value("stopped"))
            .andExpect(jsonPath("$.positionSeconds").value(0));
    }

    // ── Streaming ────────────────────────────────────────────────────────
    @Test
    void stream_fullFileAndRangeRequestForKnownTrack() throws Exception {
        String trackId = trackRegistry.listTracks().getFirst().id();
        // Full file
        mockMvc
            .perform(get("/api/stream/" + trackId))
            .andExpect(status().isOk())
            .andExpect(header().string("Accept-Ranges", "bytes"));
        // Byte range — verify partial content status and Content-Range header
        mockMvc
            .perform(get("/api/stream/" + trackId).header("Range", "bytes=0-3"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string("Content-Range", org.hamcrest.Matchers.startsWith("bytes 0-3/")));
    }
}
