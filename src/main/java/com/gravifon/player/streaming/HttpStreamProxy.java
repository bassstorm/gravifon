package com.gravifon.player.streaming;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.stream.StreamRefreshService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HttpStreamProxy implements StreamProxy {
    private final HttpClient client;
    private final StreamRefreshService refreshService;

    @Autowired
    public HttpStreamProxy(StreamRefreshService refreshService) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), refreshService);
    }

    HttpStreamProxy(HttpClient client, StreamRefreshService refreshService) {
        this.client = client;
        this.refreshService = refreshService;
    }

    @Override
    public void proxy(Track track, HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!(track instanceof StreamTrack streamTrack)) {
            throw new IllegalArgumentException("Cannot proxy non-stream track: " + track.id());
        }
        streamTrack = refreshService.ensureFresh(streamTrack);
        HttpRequest.Builder builder =
                HttpRequest.newBuilder(URI.create(streamTrack.streamUrl())).GET().timeout(Duration.ofSeconds(15));
        String range = request.getHeader("Range");
        if (range != null && !range.isBlank()) {
            builder.header("Range", range);
        }
        try {
            HttpResponse<InputStream> upstream = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            response.setStatus(upstream.statusCode());
            copyHeader(upstream, response, "Content-Type");
            copyHeader(upstream, response, "Content-Length");
            copyHeader(upstream, response, "Content-Range");
            copyHeader(upstream, response, "Accept-Ranges");
            try (var body = upstream.body()) {
                body.transferTo(response.getOutputStream());
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Stream proxy interrupted", exception);
        }
    }

    private void copyHeader(HttpResponse<?> upstream, HttpServletResponse response, String name) {
        upstream
            .headers()
            .firstValue(name)
            .ifPresent(value -> response.setHeader(name, value));
    }
}
