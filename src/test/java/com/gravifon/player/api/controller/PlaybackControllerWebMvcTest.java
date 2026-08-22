package com.gravifon.player.api.controller;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.PlaybackState;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playback.service.PlaybackService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PlaybackController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class})
class PlaybackControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlaybackService playbackService;

    @Test
    void getPlaybackState_returnsCurrentState() throws Exception {
        when(playbackService.getState()).thenReturn(state("t1", TransportState.PAUSED, 12));

        mockMvc.perform(get("/api/playback").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePlaylistId").value("all-tracks"))
                .andExpect(jsonPath("$.currentTrackId").value("t1"))
                .andExpect(jsonPath("$.playbackMode").value("sequential"))
                .andExpect(jsonPath("$.transportState").value("paused"))
                .andExpect(jsonPath("$.positionSeconds").value(12))
                .andExpect(jsonPath("$.positionOrigin").value("observed"));
    }

    @Test
    void playbackInitializationIsAnExplicitAction() throws Exception {
        when(playbackService.initializeClient()).thenReturn(state("t1", TransportState.PAUSED, 12));

        mockMvc.perform(post("/api/playback/init"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(playbackService).initializeClient();
        org.mockito.Mockito.verify(playbackService, org.mockito.Mockito.never()).getState();
    }

    @Test
    void entityReferenceCommands_delegateAndReturnUpdatedState() throws Exception {
        when(playbackService.selectPlaylist("favorites")).thenReturn(state("t2", TransportState.STOPPED, 0));
        when(playbackService.selectTrack("t1")).thenReturn(state("t1", TransportState.STOPPED, 0));
        when(playbackService.nextTrack()).thenReturn(state("t2", TransportState.PLAYING, 0));
        when(playbackService.setMode(PlaybackMode.RANDOM)).thenReturn(new PlaybackState("all-tracks", "t2", PlaybackMode.RANDOM, TransportState.PLAYING, 0));
        when(playbackService.setTransportState(TransportState.PAUSED)).thenReturn(state("t2", TransportState.PAUSED, 4));
        when(playbackService.reportPosition("t2", 4)).thenReturn(state("t2", TransportState.PAUSED, 4));

        mockMvc.perform(post("/api/playback/playlist/favorites"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activePlaylistId").value("all-tracks"));
        mockMvc.perform(post("/api/playback/track/t1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTrackId").value("t1"));
        mockMvc.perform(post("/api/playback/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentTrackId").value("t2"));
        mockMvc.perform(post("/api/playback/mode/random"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playbackMode").value("random"));
        mockMvc.perform(post("/api/playback/transport/paused"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transportState").value("paused"));
        mockMvc.perform(post("/api/playback/position")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trackId\":\"t2\",\"positionSeconds\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionSeconds").value(4));
    }

    @Test
    void legacyCommands_areNotAvailable() throws Exception {
        mockMvc.perform(post("/api/playback/play")).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/playback/seek").param("positionSeconds", "4")).andExpect(status().isNotFound());
    }

    @Test
    void setMode_rejectsInvalidModeAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/playback/mode/not-a-mode").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void setTransport_rejectsInvalidTransportAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/playback/transport/not-a-transport").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void selectTrack_outsideActivePlaylist_returnsBadRequest() throws Exception {
        doThrow(new IllegalArgumentException("Track is not in the active playlist: outside"))
                .when(playbackService).selectTrack("outside");

        mockMvc.perform(post("/api/playback/track/outside").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    private PlaybackState state(String trackId, TransportState transportState, long positionSeconds) {
        return new PlaybackState("all-tracks", trackId, PlaybackMode.SEQUENTIAL, transportState, positionSeconds);
    }
}
