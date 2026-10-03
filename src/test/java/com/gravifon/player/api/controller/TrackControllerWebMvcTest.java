package com.gravifon.player.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.error.EntityNotFoundException;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackError;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.service.TrackRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TrackController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class})
class TrackControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TrackRegistry trackRegistry;

    @Test
    void listTracks_returnsTrackDtosAndPreservesCorrelationHeader() throws Exception {
        Track track = new FileTrack("track-1", java.util.Map.of(), 123L, TrackState.healthy(), "a.mp3", "mp3");
        when(trackRegistry.listTracks()).thenReturn(List.of(track));

        mockMvc
            .perform(get("/api/tracks")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-123")
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-123"))
            .andExpect(jsonPath("$[0].id").value("track-1"))
            .andExpect(jsonPath("$[0].filename").value("a.mp3"))
            .andExpect(jsonPath("$[0].format").value("mp3"))
            .andExpect(jsonPath("$[0].durationSeconds").value(123));
    }

    @Test
    void getTrack_returns404ApiErrorWhenMissing() throws Exception {
        when(trackRegistry.getTrack("missing")).thenThrow(new EntityNotFoundException("Track not found: missing"));

        mockMvc
            .perform(get("/api/tracks/missing")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-missing")
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.error").value("Not Found"))
            .andExpect(jsonPath("$.message").value("Track not found: missing"))
            .andExpect(jsonPath("$.path").value("/api/tracks/missing"))
            .andExpect(jsonPath("$.correlationId").value("cid-missing"));
    }

    @Test
    void getTrack_returns500ApiErrorForUnexpectedFailure() throws Exception {
        when(trackRegistry.getTrack("boom")).thenThrow(new RuntimeException("unexpected"));

        mockMvc
            .perform(get("/api/tracks/boom")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-500")
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.error").value("Internal Server Error"))
            .andExpect(jsonPath("$.message").value("Unexpected server error"))
            .andExpect(jsonPath("$.path").value("/api/tracks/boom"))
            .andExpect(jsonPath("$.correlationId").value("cid-500"));
    }

    @Test
    void updateMetadata_appliesValidMetadata() throws Exception {
        Track updated =
                new FileTrack(
                        "track-1",
                        Map.of("TITLE", List.of("Updated")),
                        123L,
                        TrackState.healthy(),
                        "a.mp3",
                        "mp3"
        );
        when(trackRegistry.updateMetadata("track-1", Map.of("TITLE", List.of("Updated")))).thenReturn(updated);

        mockMvc
            .perform(patch("/api/tracks/track-1/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metadata\":{\"TITLE\":[\"Updated\"]}}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("track-1"))
            .andExpect(jsonPath("$.metadata.TITLE[0]").value("Updated"));
    }

    @Test
    void updateMetadata_rejectsMissingMetadata() throws Exception {
        mockMvc
            .perform(patch("/api/tracks/track-1/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metadata\":null}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("metadata: must not be null"));
    }

    @Test
    void updateMetadata_returns404WhenTrackIsMissing() throws Exception {
        when(trackRegistry.updateMetadata("missing", Map.of()))
            .thenThrow(new EntityNotFoundException("Track not found: missing"));

        mockMvc
            .perform(patch("/api/tracks/missing/metadata")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"metadata\":{}}")
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Track not found: missing"));
    }

    @Test
    void reportState_recordsAndClearsTrackFailure() throws Exception {
        Track failing = new FileTrack(
                "track-1",
                Map.of(),
                123L,
                new TrackState(true, new TrackError("UPSTREAM", "Unavailable", Instant.EPOCH, "CLIENT")),
                "a.mp3",
                "mp3"
        );
        Track healthy = new FileTrack("track-1", Map.of(), 123L, TrackState.healthy(), "a.mp3", "mp3");
        when(trackRegistry.reportFailure("track-1", "UPSTREAM", "Unavailable")).thenReturn(failing);
        when(trackRegistry.clearError("track-1")).thenReturn(healthy);

        mockMvc
            .perform(post("/api/tracks/track-1/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"UPSTREAM\",\"message\":\"Unavailable\",\"clear\":false}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state.failing").value(true))
            .andExpect(jsonPath("$.state.lastError.kind").value("UPSTREAM"));

        mockMvc
            .perform(post("/api/tracks/track-1/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"clear\":true}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state.failing").value(false));
    }

    @Test
    void reportState_returns404WhenTrackIsMissing() throws Exception {
        when(trackRegistry.reportFailure("missing", "CLIENT_ERROR", "Unavailable"))
            .thenThrow(new EntityNotFoundException("Track not found: missing"));

        mockMvc
            .perform(post("/api/tracks/missing/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"CLIENT_ERROR\",\"message\":\"Unavailable\"}")
            )
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Track not found: missing"));
    }

    @Test
    void reportState_rejectsFailureWithoutMessage() throws Exception {
        mockMvc
            .perform(post("/api/tracks/track-1/state")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kind\":\"UPSTREAM\",\"message\":\" \"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400));

        verify(trackRegistry, org.mockito.Mockito.never()).reportFailure(anyString(), any(), any());
    }

    @Test
    void updateMetadata_rejectsInvalidNestedValues() throws Exception {
        for (String body :
                List.of(
                        "{\"metadata\":{\"TITLE\":[\"\"]}}",
                        "{\"metadata\":{\"TITLE\":[]}}",
                        "{\"metadata\":{\" \":[\"valid\"]}}"
        )) {
            mockMvc
                .perform(patch("/api/tracks/track-1/metadata").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
        }

        verify(trackRegistry, org.mockito.Mockito.never()).updateMetadata(anyString(), any());
    }
}
