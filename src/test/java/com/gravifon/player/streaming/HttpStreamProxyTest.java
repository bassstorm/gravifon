package com.gravifon.player.streaming;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.gravifon.player.config.StreamRefreshProperties;
import com.gravifon.player.registry.model.StreamTrack;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.stream.StreamTrackRefresher;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpStreamProxyTest {
    private final StreamTrackRefresher refresher = mock(StreamTrackRefresher.class);
    private final StreamRefreshProperties refreshProperties =
            new StreamRefreshProperties(Duration.ofHours(1), 10, Duration.ofMinutes(1), Duration.ofSeconds(5), 2);
    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private HttpStreamProxy proxy;
    private final StreamTrack track = new StreamTrack(
            "t1",
            new TrackMetadata(Map.of()),
            null,
            TrackState.healthy(),
            URI.create("https://source"),
            URI.create("https://stream"),
            Instant.now().plusSeconds(60)
    );

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).ignoreExpectOrder(true).build();
        proxy = new HttpStreamProxy(restClientBuilder.build(), refresher, refreshProperties);
    }

    @Test
    void forwardsRangeAndUpstreamPartialResponse() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(refresher.ensureValid(track, refreshProperties.refreshMinValidity())).thenReturn(track);
        when(request.getHeader("Range")).thenReturn("bytes=2-4");
        mockServer
            .expect(requestTo("https://stream"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Range", "bytes=2-4"))
            .andRespond(withStatus(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("Content-Range", "bytes 2-4/10")
                .header("Accept-Ranges", "bytes")
                .header("Content-Length", "3")
                .body("abc")
            );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStream(output));

        proxy.proxy(track, request, response);

        mockServer.verify();
        verify(response).setStatus(206);
        verify(response).setHeader("Content-Range", "bytes 2-4/10");
        verify(response).setHeader("Content-Type", "audio/mpeg");
        verify(response).setHeader("Content-Length", "3");
        verify(response).setHeader("Accept-Ranges", "bytes");
        assertArrayEquals("abc".getBytes(), output.toByteArray());
    }

    @Test
    void refreshFailureDoesNotOpenUpstream() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(refresher.ensureValid(track, refreshProperties.refreshMinValidity())).thenThrow(
                new IOException("refresh failed")
        );

        assertThrows(IOException.class, () -> proxy.proxy(track, request, response));
        mockServer.verify();
    }

    @Test
    void forwardsFullUpstreamResponseWhenRangeIsNotSupported() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(refresher.ensureValid(track, refreshProperties.refreshMinValidity())).thenReturn(track);
        when(request.getHeader("Range")).thenReturn("bytes=2-4");
        mockServer
            .expect(requestTo("https://stream"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.OK)
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body("all-bytes"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStream(output));

        proxy.proxy(track, request, response);

        mockServer.verify();
        verify(response).setStatus(200);
        assertArrayEquals("all-bytes".getBytes(), output.toByteArray());
    }

    @Test
    void passesThroughUpstreamFailureStatusWithoutOpeningARefreshPath() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(refresher.ensureValid(track, refreshProperties.refreshMinValidity())).thenReturn(track);
        mockServer
            .expect(requestTo("https://stream"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body("unavailable"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        when(response.getOutputStream()).thenReturn(outputStream(output));

        proxy.proxy(track, request, response);

        mockServer.verify();
        verify(response).setStatus(503);
        assertArrayEquals("unavailable".getBytes(), output.toByteArray());
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
}
