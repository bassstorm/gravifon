package com.gravifon.player.api.controller;

import com.gravifon.player.api.interceptor.ClientSessionInterceptor;
import com.gravifon.player.api.model.SessionAcquireRequest;
import com.gravifon.player.api.model.SessionResponse;
import com.gravifon.player.config.SessionProperties;
import com.gravifon.player.session.model.Caller;
import com.gravifon.player.session.model.Username;
import com.gravifon.player.session.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessionService;
    private final SessionProperties properties;

    @PostMapping
    public ResponseEntity<SessionResponse> acquire(@Valid @RequestBody SessionAcquireRequest request) {
        var acquisition = sessionService.acquire(Username.from(request.username()));
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(SessionResponse.from(acquisition, properties.inactivityTimeout()));
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> invalidateCurrent(
            @RequestAttribute(ClientSessionInterceptor.CALLER_ATTRIBUTE) Caller caller
    ) {
        sessionService.invalidate(caller);
        return ResponseEntity.noContent().build();
    }
}
