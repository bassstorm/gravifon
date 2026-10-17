package com.gravifon.player.api.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.api.interceptor.ClientSessionInterceptor;
import com.gravifon.player.api.interceptor.ClientSessionWebMvcConfiguration;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.service.StreamTrackRegistrar;
import com.gravifon.player.registry.stream.StreamResolverRegistry;
import com.gravifon.player.session.model.Caller;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.UserId;
import com.gravifon.player.session.service.SessionService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = StreamRegistrationController.class)
@Import({
        ApiExceptionHandler.class,
        CorrelationIdFilter.class,
        ClientSessionInterceptor.class,
        ClientSessionWebMvcConfiguration.class
})
class StreamRegistrationControllerWebMvcTest {
    private static final String SESSION_ID = "session-1";
    private static final String SOURCE_URL = "https://example.com/album";
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private StreamTrackRegistrar registrar;

    @BeforeEach
    void authenticateSession() {
        when(sessionService.validateAndRenew(new ClientSessionId(SESSION_ID)))
            .thenReturn(Optional.of(new Caller(new ClientSessionId(SESSION_ID), new UserId("user-1"))));
    }

    @Test
    void registerSourcesReturnsOrderedTrackIdsIncludingDuplicates() throws Exception {
        Track first = streamTrack("track-1");
        Track second = streamTrack("track-2");
        String httpSourceUrl = "http://example.com/second";
        when(registrar.registerSources(List.of(SOURCE_URL, httpSourceUrl))).thenReturn(List.of(first, second, first));

        mockMvc
            .perform(
                    registerRequest(
                            """
                {"sourceUrls":["https://example.com/album","http://example.com/second"]}
                """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trackIds").isArray())
            .andExpect(jsonPath("$.trackIds").value(Matchers.contains("track-1", "track-2", "track-1")));
    }

    @Test
    void registerSourcesRejectsEmptyOrBlankEntries() throws Exception {
        mockMvc
            .perform(registerRequest("{\"sourceUrls\":[]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        mockMvc
            .perform(registerRequest("{\"sourceUrls\":[\"  \"]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verifyNoInteractions(registrar);
    }

    @Test
    void unsupportedResolverIsAnExplicitInputErrorWithoutEchoingTheUrl() throws Exception {
        when(registrar.registerSources(List.of(SOURCE_URL)))
            .thenThrow(new StreamResolverRegistry.NoStreamResolverException(SOURCE_URL));

        mockMvc
            .perform(registerRequest("""
                {"sourceUrls":["https://example.com/album"]}
                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(content().string(Matchers.not(Matchers.containsString(SOURCE_URL))));
    }

    @Test
    void resolverFailureIsAnExplicitServerErrorRatherThanAnEmptySuccess() throws Exception {
        when(registrar.registerSources(List.of(SOURCE_URL))).thenThrow(new IllegalStateException("upstream failure"));

        mockMvc
            .perform(registerRequest("""
                {"sourceUrls":["https://example.com/album"]}
                """))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("Unexpected server error"))
            .andExpect(content().string(Matchers.not(Matchers.containsString(SOURCE_URL))));
    }

    private MockHttpServletRequestBuilder registerRequest(String body) {
        return post("/api/tracks/streams")
            .header("X-Client-Session", SESSION_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    }

    private static StreamTrack streamTrack(String id) {
        return new StreamTrack(
                id,
                Map.of("TITLE", List.of(id)),
                100L,
                TrackState.healthy(),
                "https://example.com/" + id,
                null,
                null
        );
    }
}
