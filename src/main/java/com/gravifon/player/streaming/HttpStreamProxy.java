package com.gravifon.player.streaming;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.stream.StreamTrackRefresher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class HttpStreamProxy implements StreamProxy {
    private final RestClient client;
    private final StreamTrackRefresher refresher;
    private final StreamRefreshProperties refreshProperties;

    public HttpStreamProxy(
            @Qualifier("httpStreamProxyRestClient") RestClient client,
            StreamTrackRefresher refresher,
            StreamRefreshProperties refreshProperties
    ) {
        this.client = client;
        this.refresher = refresher;
        this.refreshProperties = refreshProperties;
    }

    @Override
    public void proxy(StreamTrack track, HttpServletRequest request, HttpServletResponse response) throws IOException {
        StreamTrack streamTrack = refresher.ensureValid(track, refreshProperties.refreshMinValidity());
        String range = request.getHeader(HttpHeaders.RANGE);

        client
            .get()
            .uri(streamTrack.streamUrl())
            .headers(headers -> {
                if (StringUtils.hasText(range)) {
                    headers.set(HttpHeaders.RANGE, range);
                }
            })
            .exchange((clientRequest, clientResponse) -> {
                response.setStatus(clientResponse.getStatusCode().value());
                copyHeader(clientResponse, response, HttpHeaders.CONTENT_TYPE);
                copyHeader(clientResponse, response, HttpHeaders.CONTENT_LENGTH);
                copyHeader(clientResponse, response, HttpHeaders.CONTENT_RANGE);
                copyHeader(clientResponse, response, HttpHeaders.ACCEPT_RANGES);
                try (InputStream body = clientResponse.getBody()) {
                    body.transferTo(response.getOutputStream());
                }
                return null;
            });
    }

    private void copyHeader(ClientHttpResponse upstream, HttpServletResponse response, String name) {
        List<String> values = upstream.getHeaders().get(name);
        if (values != null && !values.isEmpty()) {
            response.setHeader(name, values.getFirst());
        }
    }
}
