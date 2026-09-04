package com.gravifon.player.streaming;

import com.gravifon.player.playback.service.PlaybackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AudioStreamingService {

    private final PlaybackService playbackService;

    public void streamFile(String trackId, Path trackPath, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        long fileLength;
        try (FileChannel probe = FileChannel.open(trackPath, StandardOpenOption.READ)) {
            fileLength = probe.size();
        }

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        ByteRange range = (rangeHeader == null || rangeHeader.isBlank()) ? null : parseRange(rangeHeader, fileLength);

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
        response.setHeader(
                HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(range.start(), range.end(), fileLength));
        response.setContentLengthLong(regionLength);

        try (FileChannel channel = FileChannel.open(trackPath, StandardOpenOption.READ)) {
            channel.transferTo(range.start(), regionLength, Channels.newChannel(response.getOutputStream()));
        }
        playbackService.observeStream(trackId, range.end(), fileLength);
    }

    public ByteRange parseRange(String rawRange, long fileLength) {
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
                "Range not satisfiable. Expected bytes within resource length %d".formatted(fileLength));
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

    public record ByteRange(long start, long end) {}
}
