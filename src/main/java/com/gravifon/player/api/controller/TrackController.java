package com.gravifon.player.api.controller;

import com.gravifon.player.api.error.ResourceNotFoundException;
import com.gravifon.player.api.model.TrackResponse;
import com.gravifon.player.catalog.service.MediaCatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    private final MediaCatalogService mediaCatalogService;

    public TrackController(MediaCatalogService mediaCatalogService) {
        this.mediaCatalogService = mediaCatalogService;
    }

    @GetMapping
    public List<TrackResponse> listTracks() {
        return mediaCatalogService.listTracks().stream().map(TrackResponse::from).toList();
    }

    @GetMapping("/{trackId}")
    public TrackResponse getTrack(@PathVariable String trackId) {
        return mediaCatalogService.findTrackById(trackId)
                .map(TrackResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
    }
}

