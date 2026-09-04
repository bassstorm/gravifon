package com.gravifon.player.api.controller;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playback.service.PlaybackService;
import com.gravifon.player.playlist.model.Playlist;
import com.gravifon.player.playlist.service.PlaylistService;
import com.gravifon.player.registry.service.StreamTrackRegistrar;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = PlaylistController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class})
class PlaylistControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private PlaylistService playlistService;
    @MockitoBean
    private PlaybackService playbackService;
    @MockitoBean
    private StreamTrackRegistrar streamTrackRegistrar;

    @Test
    void listPlaylists_returnsDtosWithActiveFlag() throws Exception {
        Playlist active = new Playlist("all-tracks", "All Tracks", List.of("t1", "t2"));
        Playlist other = new Playlist("favorites", "Favorites", List.of("t2"));

        when(playlistService.getActive()).thenReturn(active);
        when(playlistService.activeId()).thenReturn(java.util.Optional.of("all-tracks"));
        when(playlistService.listPlaylists()).thenReturn(List.of(active, other));

        mockMvc
            .perform(get("/api/playlists")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-playlists")
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value("all-tracks"))
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[1].id").value("favorites"))
            .andExpect(jsonPath("$[1].active").value(false));
    }

    @Test
    void getPlaylist_returnsSinglePlaylist() throws Exception {
        Playlist active = new Playlist("all-tracks", "All Tracks", List.of("t1"));

        when(playlistService.getPlaylist("all-tracks")).thenReturn(active);
        when(playlistService.getActive()).thenReturn(active);
        when(playlistService.activeId()).thenReturn(java.util.Optional.of("all-tracks"));

        mockMvc
            .perform(get("/api/playlists/all-tracks").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("all-tracks"))
            .andExpect(jsonPath("$.mode").value("SEQUENTIAL"))
            .andExpect(jsonPath("$.name").value("All Tracks"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void selectPlaylist_returnsSelectedPlaylistAsActive() throws Exception {
        when(playbackService.selectPlaylist("favorites"))
            .thenReturn(new PlaybackState("favorites", "t2", PlaybackMode.SEQUENTIAL, TransportState.STOPPED, 0));

        mockMvc
            .perform(post("/api/playlists/favorites/select").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activePlaylistId").value("favorites"))
            .andExpect(jsonPath("$.currentTrackId").value("t2"));
    }

    @Test
    void playlistMutationEndpoints_persistChanges() throws Exception {
        Playlist playlist = new Playlist("p1", "Playlist", List.of());
        when(playlistService.create(any(), any(), any())).thenReturn(playlist);
        when(playlistService.getPlaylist("p1")).thenReturn(playlist);
        when(playlistService.getActive()).thenReturn(playlist);
        when(playlistService.rename("p1", "Renamed")).thenReturn(playlist);

        mockMvc
            .perform(post("/api/playlists")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\\\"name\\\":" + "\\\"Created\\\"}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("p1"));

        mockMvc
            .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/playlists/p1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}")
            )
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/playlists/p1")).andExpect(status().isNoContent());
    }
}
