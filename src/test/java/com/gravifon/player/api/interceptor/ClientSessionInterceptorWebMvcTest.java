package com.gravifon.player.api.interceptor;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.api.controller.AudioStreamingController;
import com.gravifon.player.api.controller.TrackController;
import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.service.TrackRegistry;
import com.gravifon.player.session.model.Caller;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.UserId;
import com.gravifon.player.session.service.SessionService;
import com.gravifon.player.streaming.AudioStreamingService;
import com.gravifon.player.streaming.StreamProxy;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {TrackController.class, AudioStreamingController.class})
@Import({
        ApiExceptionHandler.class,
        CorrelationIdFilter.class,
        ClientSessionInterceptor.class,
        ClientSessionWebMvcConfiguration.class
})
class ClientSessionInterceptorWebMvcTest {
    private static final String SESSION_HEADER = "X-Client-Session";
    private static final Caller CALLER = new Caller(new ClientSessionId("session-1"), new UserId("user-1"));
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private TrackRegistry trackRegistry;
    @MockitoBean
    private StreamProxy streamProxy;
    @MockitoBean
    private AudioStreamingService audioStreamingService;

    @Test
    void validHeaderAuthenticatesApiRequestAndRenewsAtInitiation() throws Exception {
        when(sessionService.validateAndRenew(new ClientSessionId("session-1"))).thenReturn(Optional.of(CALLER));

        mockMvc.perform(get("/api/tracks").header(SESSION_HEADER, "session-1")).andExpect(status().isOk());

        verify(sessionService).validateAndRenew(new ClientSessionId("session-1"));
    }

    @Test
    void mediaQueryAuthenticatesRangeRequest() throws Exception {
        Track track = new FileTrack("track-1", Map.of(), null, TrackState.healthy(), "track.mp3", "mp3");
        when(sessionService.validateAndRenew(new ClientSessionId("session-1"))).thenReturn(Optional.of(CALLER));
        when(trackRegistry.getTrack("track-1")).thenReturn(track);

        mockMvc
            .perform(get("/api/stream/track-1").queryParam("session", "session-1").header("Range", "bytes=0-1"))
            .andExpect(status().isOk());

        verify(sessionService).validateAndRenew(new ClientSessionId("session-1"));
        verify(audioStreamingService).streamFile(any(), any(), any());
    }

    @Test
    void matchingHeaderAndMediaQueryAreAccepted() throws Exception {
        Track track = new FileTrack("track-1", Map.of(), null, TrackState.healthy(), "track.mp3", "mp3");
        when(sessionService.validateAndRenew(new ClientSessionId("session-1"))).thenReturn(Optional.of(CALLER));
        when(trackRegistry.getTrack("track-1")).thenReturn(track);

        mockMvc
            .perform(get("/api/stream/track-1").header(SESSION_HEADER, "session-1").queryParam("session", "session-1"))
            .andExpect(status().isOk());
    }

    @Test
    void conflictingOrDuplicatePresentationIsRejected() throws Exception {
        mockMvc
            .perform(get("/api/stream/track-1").header(SESSION_HEADER, "one").queryParam("session", "two"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.path").value("/api/stream/track-1"))
            .andExpect(content().string(Matchers.not(Matchers.containsString("two"))));
        mockMvc.perform(get("/api/stream/track-1").queryParam("session", "one", "one")).andExpect(status()
            .isBadRequest()
        );
        mockMvc.perform(get("/api/tracks").header(SESSION_HEADER, "one", "one")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tracks").queryParam("session", "one")).andExpect(status().isBadRequest());

        verify(sessionService, never()).validateAndRenew(any());
    }

    @Test
    void missingOrUnknownSessionIsUnauthorizedAndRetainsCorrelationWithoutEchoingToken() throws Exception {
        mockMvc
            .perform(get("/api/tracks").header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-denied"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-denied"))
            .andExpect(jsonPath("$.correlationId").value("cid-denied"));

        when(sessionService.validateAndRenew(new ClientSessionId("secret-token"))).thenReturn(Optional.empty());
        mockMvc
            .perform(get("/api/tracks")
                .header(SESSION_HEADER, "secret-token")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-invalid")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"))
            .andExpect(content().string(Matchers.not(Matchers.containsString("secret-token"))))
            .andExpect(jsonPath("$.path").value("/api/tracks"))
            .andExpect(jsonPath("$.correlationId").value("cid-invalid"));

        mockMvc
            .perform(get("/api/stream/track-1")
                .queryParam("session", "secret-token")
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "cid-query")
            )
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(Matchers.not(Matchers.containsString("secret-token"))))
            .andExpect(jsonPath("$.path").value("/api/stream/track-1"))
            .andExpect(jsonPath("$.correlationId").value("cid-query"));
    }

    @Test
    void nonApiAndActuatorPathsDoNotRequireClientSessions() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        verify(sessionService, never()).validateAndRenew(any());
    }
}
