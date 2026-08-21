package com.gravifon.player.api.controller;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.catalog.model.Track;
import com.gravifon.player.catalog.service.MediaCatalogService;
import com.gravifon.player.observability.CorrelationIdFilter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TrackController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class})
class TrackControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaCatalogService mediaCatalogService;

    @Test
    void listTracks_returnsTrackDtosAndPreservesCorrelationHeader() throws Exception {
        Track track = new Track("track-1", Path.of("/music/a.mp3"), "a.mp3", "mp3", 123L);
        when(mediaCatalogService.listTracks()).thenReturn(List.of(track));

        mockMvc.perform(get("/api/tracks")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-123")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-123"))
                .andExpect(jsonPath("$[0].id").value("track-1"))
                .andExpect(jsonPath("$[0].filename").value("a.mp3"))
                .andExpect(jsonPath("$[0].format").value("mp3"))
                .andExpect(jsonPath("$[0].durationSeconds").value(123));
    }

    @Test
    void getTrack_returns404ApiErrorWhenMissing() throws Exception {
        when(mediaCatalogService.findTrackById("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/tracks/missing")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-missing")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Track not found: missing"))
                .andExpect(jsonPath("$.path").value("/api/tracks/missing"))
                .andExpect(jsonPath("$.correlationId").value("cid-missing"));
    }

    @Test
    void getTrack_returns500ApiErrorForUnexpectedFailure() throws Exception {
        when(mediaCatalogService.findTrackById("boom")).thenThrow(new RuntimeException("unexpected"));

        mockMvc.perform(get("/api/tracks/boom")
                        .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-500")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("Unexpected server error"))
                .andExpect(jsonPath("$.path").value("/api/tracks/boom"))
                .andExpect(jsonPath("$.correlationId").value("cid-500"));
    }
}

