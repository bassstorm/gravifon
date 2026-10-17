package com.gravifon.player.api.controller;

import com.gravifon.player.api.model.StreamRegistrationRequest;
import com.gravifon.player.api.model.StreamRegistrationResponse;
import com.gravifon.player.registry.service.StreamTrackRegistrar;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class StreamRegistrationController {
    private final StreamTrackRegistrar registrar;

    @PostMapping("/streams")
    public ResponseEntity<StreamRegistrationResponse> registerSources(
            @Valid @RequestBody StreamRegistrationRequest request
    ) {
        return ResponseEntity.ok(StreamRegistrationResponse.from(registrar.registerSources(request
            .sourceUrls()
            .stream()
            .map(URI::create)
            .toList()
        )));
    }
}
