package com.gravifon.player.api.controller;

import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.service.TrackRegistry;
import com.gravifon.player.streaming.AudioStreamingService;
import com.gravifon.player.streaming.StreamProxy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
public class AudioStreamingController {
    private final TrackRegistry trackRegistry;
    private final StreamProxy streamProxy;
    private final AudioStreamingService audioStreamingService;

    @GetMapping("/{trackId}")
    public void streamTrack(@PathVariable String trackId, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Track track = trackRegistry.getTrack(trackId);
        if (track.kind() == TrackKind.STREAM) {
            streamProxy.proxy(track, request, response);
            return;
        }
        Path trackPath = trackRegistry.resolveTrackPath(trackId);

        audioStreamingService.streamFile(trackId, trackPath, request, response);
    }
}
