package com.gravifon.player.api.controller;

import com.gravifon.player.api.error.ResourceNotFoundException;
import com.gravifon.player.registry.service.TrackRegistry;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.model.TrackKind;
import com.gravifon.player.registry.stream.StreamProxy;
import com.gravifon.player.playback.service.PlaybackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/stream")
public class AudioStreamingController {

    private final TrackRegistry trackRegistry;
    private final PlaybackService playbackService;
    private final StreamProxy streamProxy;

    public AudioStreamingController(TrackRegistry trackRegistry, PlaybackService playbackService, StreamProxy streamProxy) {
        this.trackRegistry = trackRegistry;
        this.playbackService = playbackService;
        this.streamProxy = streamProxy;
    }

    @GetMapping("/{trackId}")
    public void streamTrack(
            @PathVariable String trackId,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {

        // Resolve and validate everything before touching the response, so any
        // exception can still be handled cleanly by the @RestControllerAdvice.
        Track track = trackRegistry.findTrackById(trackId)
            .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));
        if (track.kind() == TrackKind.STREAM) {
            streamProxy.proxy(track, request, response);
            return;
        }
        Path trackPath = trackRegistry.resolveTrackPath(trackId)
            .orElseThrow(() -> new ResourceNotFoundException("Track not found: " + trackId));

        long fileLength;
        try (FileChannel probe = FileChannel.open(trackPath, StandardOpenOption.READ)) {
            fileLength = probe.size();
        }

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        ByteRange range = (rangeHeader == null || rangeHeader.isBlank())
                ? null
                : parseRange(rangeHeader, fileLength);

        // All validation passed — now commit headers and stream.
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setContentType(resolveContentType(trackPath).toString());

        if (range == null) {
            response.setContentLengthLong(fileLength);
            try (FileChannel channel = FileChannel.open(trackPath, StandardOpenOption.READ)) {
                channel.transferTo(0, fileLength, Channels.newChannel(response.getOutputStream()));
            }
            playbackService.observeStream(trackId, fileLength - 1, fileLength);
            return;
        }

        long regionLength = range.end() - range.start() + 1;
        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader(HttpHeaders.CONTENT_RANGE,
                "bytes %d-%d/%d".formatted(range.start(), range.end(), fileLength));
        response.setContentLengthLong(regionLength);

        try (FileChannel channel = FileChannel.open(trackPath, StandardOpenOption.READ)) {
            channel.transferTo(range.start(), regionLength, Channels.newChannel(response.getOutputStream()));
        }
        playbackService.observeStream(trackId, range.end(), fileLength);
    }

    private ByteRange parseRange(String rawRange, long fileLength) {
        if (!rawRange.startsWith("bytes=")) {
            throw rangeNotSatisfiable(fileLength);
        }

        String value = rawRange.substring("bytes=".length()).trim();
        int dash = value.indexOf('-');
        if (dash < 0) {
            throw rangeNotSatisfiable(fileLength);
        }

        String startPart = value.substring(0, dash).trim();
        String endPart = value.substring(dash + 1).trim();

        try {
            long start;
            long end;

            if (startPart.isEmpty()) {
                long suffixLength = Long.parseLong(endPart);
                if (suffixLength <= 0) {
                    throw rangeNotSatisfiable(fileLength);
                }
                start = Math.max(0, fileLength - suffixLength);
                end = fileLength - 1;
            } else {
                start = Long.parseLong(startPart);
                end = endPart.isEmpty() ? fileLength - 1 : Long.parseLong(endPart);
            }

            if (start < 0 || start >= fileLength || end < start) {
                throw rangeNotSatisfiable(fileLength);
            }

            return new ByteRange(start, Math.min(end, fileLength - 1));
        } catch (NumberFormatException ex) {
            throw rangeNotSatisfiable(fileLength);
        }
    }

    private ResponseStatusException rangeNotSatisfiable(long fileLength) {
        return new ResponseStatusException(
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                "Range not satisfiable. Expected bytes within resource length %d".formatted(fileLength)
        );
    }

    private MediaType resolveContentType(Path path) {
        String filename = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".mp3")) {
            return MediaType.valueOf("audio/mpeg");
        }
        if (filename.endsWith(".flac")) {
            return MediaType.valueOf("audio/flac");
        }
        if (filename.endsWith(".ogg")) {
            return MediaType.valueOf("audio/ogg");
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private record ByteRange(long start, long end) {
    }
}

