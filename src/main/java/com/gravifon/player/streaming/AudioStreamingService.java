package com.gravifon.player.streaming;

import com.gravifon.player.playback.service.PlaybackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AudioStreamingService {
    private static final MediaType FLAC_MEDIA_TYPE = MediaType.parseMediaType("audio/flac");
    private final PlaybackService playbackService;

    public void streamFile(String trackId, Path trackPath, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        long fileLength;
        try (FileChannel probe = FileChannel.open(trackPath, StandardOpenOption.READ)) {
            fileLength = probe.size();
        }

        String rangeHeader = request.getHeader(HttpHeaders.RANGE);
        ResourceRegion region = StringUtils.hasText(rangeHeader)
                ? parseSingleRegion(rangeHeader, new FileSystemResource(trackPath), fileLength)
                : null;

        response.setHeader(HttpHeaders.ACCEPT_RANGES, "bytes");
        response.setContentType(resolveContentType(trackPath).toString());

        if (region == null) {
            response.setContentLengthLong(fileLength);
            try (FileChannel channel = FileChannel.open(trackPath, StandardOpenOption.READ)) {
                channel.transferTo(0, fileLength, Channels.newChannel(response.getOutputStream()));
            }
            playbackService.observeStream(trackId, fileLength - 1, fileLength);
            return;
        }

        long start = region.getPosition();
        long count = region.getCount();
        long end = start + count - 1;

        response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
        response.setHeader(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(start, end, fileLength));
        response.setContentLengthLong(count);

        try (FileChannel channel = FileChannel.open(trackPath, StandardOpenOption.READ)) {
            channel.transferTo(start, count, Channels.newChannel(response.getOutputStream()));
        }
        playbackService.observeStream(trackId, end, fileLength);
    }

    public ResourceRegion parseSingleRegion(String rawRange, Resource resource, long fileLength) {
        List<HttpRange> ranges;
        try {
            ranges = HttpRange.parseRanges(rawRange);
        } catch (IllegalArgumentException ex) {
            throw rangeNotSatisfiable(fileLength);
        }

        if (ranges.size() != 1) {
            throw rangeNotSatisfiable(fileLength);
        }

        HttpRange range = ranges.getFirst();
        try {
            ResourceRegion region = range.toResourceRegion(resource);
            if (region.getPosition() >= fileLength || region.getCount() <= 0) {
                throw rangeNotSatisfiable(fileLength);
            }
            return region;
        } catch (IllegalArgumentException ex) {
            throw rangeNotSatisfiable(fileLength);
        }
    }

    private ResponseStatusException rangeNotSatisfiable(long fileLength) {
        return new RangeNotSatisfiableException(fileLength);
    }

    private MediaType resolveContentType(Path path) {
        String filename = path.getFileName().toString();
        if (filename.toLowerCase(Locale.ROOT).endsWith(".flac")) {
            return FLAC_MEDIA_TYPE;
        }
        return MediaTypeFactory.getMediaType(filename).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }
}
