package com.gravifon.player.api.controller;

import com.gravifon.player.api.model.TrackResponse;
import com.gravifon.player.error.ResourceNotFoundException;
import com.gravifon.player.registry.service.TrackRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackRegistry trackRegistry;

    @GetMapping
    public List<TrackResponse> listTracks() {
        return trackRegistry.listTracks().stream().map(TrackResponse::from).toList();
    }

    @GetMapping("/{trackId}")
    public TrackResponse getTrack(@PathVariable String trackId) {
        return trackRegistry.findTrackById(trackId)
                .map(TrackResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
    }

    @PatchMapping("/{trackId}/metadata")
    public TrackResponse updateMetadata(@PathVariable String trackId,
                                        @RequestBody com.gravifon.player.api.model.TrackMetadataUpdateRequest request) {
        return TrackResponse.from(trackRegistry.updateMetadata(trackId, request.metadata()));
    }

    @PostMapping("/{trackId}/state")
    public TrackResponse reportState(@PathVariable String trackId,
                                     @RequestBody com.gravifon.player.api.model.TrackStateReportRequest request) {
        return TrackResponse.from(trackRegistry.reportState(trackId, request.kind(), request.message(), request.clear()));
    }
}

