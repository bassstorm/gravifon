package com.gravifon.player.streaming;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.stream.StreamRefreshService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpStreamProxyTest {

    private final HttpClient client = mock(HttpClient.class);
    private final StreamRefreshService refreshService = mock(StreamRefreshService.class);
    private final HttpStreamProxy proxy = new HttpStreamProxy(client, refreshService);
    private final StreamTrack track = new StreamTrack(
            "t1",
            Map.of(),
            null,
            TrackState.healthy(),
            "https://source",
            "https://stream",
            Instant.now().plusSeconds(60));

    @Test
    void forwardsRangeAndUpstreamPartialResponse() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpResponse<InputStream> upstream = response(
                206, "abc", Map.of("Content-Range", List.of("bytes 2-4/10"), "Content-Type", List.of("audio/mpeg")));
        when(refreshService.ensureFresh(track)).thenReturn(track);
        when(request.getHeader("Range")).thenReturn("bytes=2-4");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(upstream);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(new ServletOutputStream() {
            public void write(int value) {
                output.write(value);
            }

            public boolean isReady() {
                return true;
            }

            public void setWriteListener(WriteListener listener) {}
        });

        proxy.proxy(track, request, response);

        verify(client).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(response).setStatus(206);
        verify(response).setHeader("Content-Range", "bytes 2-4/10");
        assertArrayEquals("abc".getBytes(), output.toByteArray());
    }

    @Test
    void refreshFailureDoesNotOpenUpstream() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(refreshService.ensureFresh(track)).thenThrow(new IOException("refresh failed"));

        assertThrows(IOException.class, () -> proxy.proxy(track, request, response));
        verify(client, org.mockito.Mockito.never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    void forwardsFullUpstreamResponseWhenRangeIsNotSupported() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpResponse<InputStream> upstream = response(200, "all-bytes", Map.of("Content-Type", List.of("audio/mpeg")));
        when(refreshService.ensureFresh(track)).thenReturn(track);
        when(request.getHeader("Range")).thenReturn("bytes=2-4");
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(upstream);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStream(output));

        proxy.proxy(track, request, response);

        verify(response).setStatus(200);
        assertArrayEquals("all-bytes".getBytes(), output.toByteArray());
    }

    @Test
    void passesThroughUpstreamFailureStatusWithoutOpeningARefreshPath() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpResponse<InputStream> upstream = response(503, "unavailable", Map.of());
        when(refreshService.ensureFresh(track)).thenReturn(track);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(upstream);
        when(response.getOutputStream()).thenReturn(outputStream(new ByteArrayOutputStream()));

        proxy.proxy(track, request, response);

        verify(response).setStatus(503);
    }

    private ServletOutputStream outputStream(ByteArrayOutputStream output) {
        return new ServletOutputStream() {
            public void write(int value) {
                output.write(value);
            }

            public boolean isReady() {
                return true;
            }

            public void setWriteListener(WriteListener listener) {}
        };
    }

    private HttpResponse<InputStream> response(int status, String body, Map<String, List<String>> headers) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(headers, (name, value) -> true));
        when(response.body()).thenReturn(new ByteArrayInputStream(body.getBytes()));
        return response;
    }
}
