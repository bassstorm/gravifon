package com.gravifon.player.api.controller;

import com.gravifon.player.api.model.PlaybackStateResponse;
import com.gravifon.player.api.model.PositionReportRequest;
import com.gravifon.player.playback.model.PlaybackMode;
import com.gravifon.player.playback.model.TransportState;
import com.gravifon.player.playback.service.PlaybackService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/playback")
@RequiredArgsConstructor
public class PlaybackController {

    private final PlaybackService playbackService;

    @GetMapping
    public PlaybackStateResponse getPlaybackState() {
        return PlaybackStateResponse.from(playbackService.getState());
    }

    @PostMapping("/init")
    public PlaybackStateResponse initializeClient() {
        return PlaybackStateResponse.from(playbackService.initializeClient());
    }

    @PostMapping("/playlist/{playlistId}")
    public PlaybackStateResponse selectPlaylist(@PathVariable String playlistId) {
        return PlaybackStateResponse.from(playbackService.selectPlaylist(playlistId));
    }

    @PostMapping("/track/{trackId}")
    public PlaybackStateResponse selectTrack(@PathVariable String trackId) {
        return PlaybackStateResponse.from(playbackService.selectTrack(trackId));
    }

    @PostMapping("/next")
    public PlaybackStateResponse nextTrack() {
        return PlaybackStateResponse.from(playbackService.nextTrack());
    }

    @PostMapping("/position")
    public PlaybackStateResponse reportPosition(@RequestBody PositionReportRequest request) {
        return PlaybackStateResponse.from(playbackService.reportPosition(request.trackId(), request.positionSeconds()));
    }

    @PostMapping("/mode/{mode}")
    public PlaybackStateResponse setMode(@PathVariable String mode) {
        PlaybackMode playbackMode = PlaybackMode.valueOf(mode.trim().toUpperCase());
        return PlaybackStateResponse.from(playbackService.setMode(playbackMode));
    }

    @PostMapping("/transport/{transportState}")
    public PlaybackStateResponse setTransportState(@PathVariable String transportState) {
        TransportState state = TransportState.valueOf(transportState.trim().toUpperCase());
        return PlaybackStateResponse.from(playbackService.setTransportState(state));
    }
}

