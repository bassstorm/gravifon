package com.gravifon.player.api.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.api.interceptor.ClientSessionInterceptor;
import com.gravifon.player.api.interceptor.ClientSessionWebMvcConfiguration;
import com.gravifon.player.config.SessionProperties;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.session.model.Caller;
import com.gravifon.player.session.model.ClientSession;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.model.User;
import com.gravifon.player.session.model.UserId;
import com.gravifon.player.session.model.Username;
import com.gravifon.player.session.service.SessionAcquisition;
import com.gravifon.player.session.service.SessionService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SessionController.class)
@Import({
        ApiExceptionHandler.class,
        CorrelationIdFilter.class,
        ClientSessionInterceptor.class,
        ClientSessionWebMvcConfiguration.class
})
class SessionControllerWebMvcTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration TIMEOUT = Duration.ofHours(24);
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private SessionService sessionService;
    @MockitoBean
    private SessionProperties properties;

    @Test
    void acquireIsAvailableWithoutCallerAndReturnsSessionDetails() throws Exception {
        User user = new User(new UserId("user-1"), Username.from("Alice"), NOW);
        ClientSession session = new ClientSession(new ClientSessionId("session-1"), user.id(), NOW, NOW, null);
        when(properties.inactivityTimeout()).thenReturn(TIMEOUT);
        when(sessionService.acquire(Username.from(" Alice "))).thenReturn(new SessionAcquisition(user, session));

        mockMvc
            .perform(post("/api/sessions").contentType("application/json").content("{\"username\":\" Alice \"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sessionId").value("session-1"))
            .andExpect(jsonPath("$.userId").value("user-1"))
            .andExpect(jsonPath("$.username").value("Alice"))
            .andExpect(jsonPath("$.lastActivityAt").value(NOW.toString()))
            .andExpect(jsonPath("$.expiresAt").value(NOW.plus(TIMEOUT).toString()));
    }

    @Test
    void acquireRejectsInvalidUsernameDeclaratively() throws Exception {
        mockMvc
            .perform(post("/api/sessions").contentType("application/json").content("{\"username\":\"  \"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteCurrentInvalidatesOnlyTheRequestCaller() throws Exception {
        Caller caller = new Caller(new ClientSessionId("session-1"), new UserId("user-1"));
        when(sessionService.validateAndRenew(new ClientSessionId("session-1"))).thenReturn(Optional.of(caller));

        mockMvc
            .perform(delete("/api/sessions/current").header("X-Client-Session", "session-1"))
            .andExpect(status().isNoContent());

        verify(sessionService).invalidate(caller);
    }
}
