package com.gravifon.player.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.api.interceptor.ClientSessionInterceptor;
import com.gravifon.player.error.EntityNotFoundException;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.registry.model.FileTrack;
import com.gravifon.player.registry.model.TrackMetadata;
import com.gravifon.player.registry.model.TrackState;
import com.gravifon.player.registry.service.TrackRegistry;
import com.gravifon.player.streaming.AudioStreamingService;
import com.gravifon.player.streaming.StreamProxy;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AudioStreamingController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class, AudioStreamingService.class})
class AudioStreamingControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private TrackRegistry trackRegistry;
    @MockitoBean
    private StreamProxy streamProxy;
    @MockitoBean
    private ClientSessionInterceptor clientSessionInterceptor;
    @TempDir
    Path tempDir;

    @BeforeEach
    void allowRequestsThroughSessionInterceptor() {
        when(clientSessionInterceptor.preHandle(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        ))
            .thenReturn(true);
    }

    @Test
    void streamTrack_withoutRange_returnsWholeResource() throws Exception {
        Path track = copyFixture("amber-signal.mp3");
        FileTrack fileTrack = fileTrack("t1", "mp3");
        when(trackRegistry.getTrack("t1")).thenReturn(fileTrack);
        when(trackRegistry.resolveTrackPath(fileTrack)).thenReturn(track);
        byte[] expectedBytes = Files.readAllBytes(track);

        mockMvc
            .perform(get("/api/stream/t1").accept(MediaType.ALL))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
            .andExpect(content().bytes(expectedBytes));
    }

    @Test
    void streamTrack_flacUsesAudioFlacContentType() throws Exception {
        Path track = copyFixture("blue-current.flac");
        FileTrack fileTrack = fileTrack("t1", "flac");
        when(trackRegistry.getTrack("t1")).thenReturn(fileTrack);
        when(trackRegistry.resolveTrackPath(fileTrack)).thenReturn(track);

        mockMvc
            .perform(get("/api/stream/t1"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/flac"));
    }

    @Test
    void streamTrack_withClosedRange_returnsPartialContent() throws Exception {
        Path track = copyFixture("amber-signal.mp3");
        FileTrack fileTrack = fileTrack("t1", "mp3");
        when(trackRegistry.getTrack("t1")).thenReturn(fileTrack);
        when(trackRegistry.resolveTrackPath(fileTrack)).thenReturn(track);
        byte[] expectedBytes = Files.readAllBytes(track);

        mockMvc
            .perform(get("/api/stream/t1").header(HttpHeaders.RANGE, "bytes=2-5"))
            .andExpect(status().isPartialContent())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/" + expectedBytes.length))
            .andExpect(content().bytes(Arrays.copyOfRange(expectedBytes, 2, 6)));
    }

    @Test
    void streamTrack_withOpenEndedRange_returnsPartialContentToEnd() throws Exception {
        Path track = copyFixture("green-air.ogg");
        FileTrack fileTrack = fileTrack("t2", "ogg");
        when(trackRegistry.getTrack("t2")).thenReturn(fileTrack);
        when(trackRegistry.resolveTrackPath(fileTrack)).thenReturn(track);
        byte[] expectedBytes = Files.readAllBytes(track);

        mockMvc
            .perform(get("/api/stream/t2").header(HttpHeaders.RANGE, "bytes=6-"))
            .andExpect(status().isPartialContent())
            .andExpect(header()
                .string(HttpHeaders.CONTENT_RANGE, "bytes 6-"
                        + (expectedBytes.length - 1)
                        + "/"
                        + expectedBytes.length)
            )
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/ogg"))
            .andExpect(content().bytes(Arrays.copyOfRange(expectedBytes, 6, expectedBytes.length)));
    }

    @Test
    void streamTrack_withUnsatisfiableRange_returns416() throws Exception {
        Path track = copyFixture("blue-current.flac");
        FileTrack fileTrack = fileTrack("t3", "flac");
        when(trackRegistry.getTrack("t3")).thenReturn(fileTrack);
        when(trackRegistry.resolveTrackPath(fileTrack)).thenReturn(track);
        long fileLength = Files.size(track);

        mockMvc
            .perform(get("/api/stream/t3").header(HttpHeaders.RANGE, "bytes=" + fileLength + "-" + (fileLength + 20)))
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength))
            .andExpect(jsonPath("$.status").value(416))
            .andExpect(jsonPath("$.code").value("RANGE_NOT_SATISFIABLE"));
    }

    @Test
    void streamTrack_withMultipartRange_returns416() throws Exception {
        Path track = copyFixture("blue-current.flac");
        FileTrack fileTrack = fileTrack("t3", "flac");
        when(trackRegistry.getTrack("t3")).thenReturn(fileTrack);
        when(trackRegistry.resolveTrackPath(fileTrack)).thenReturn(track);
        long fileLength = Files.size(track);

        mockMvc
            .perform(get("/api/stream/t3").header(HttpHeaders.RANGE, "bytes=0-1,3-4"))
            .andExpect(status().isRequestedRangeNotSatisfiable())
            .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */" + fileLength))
            .andExpect(jsonPath("$.status").value(416))
            .andExpect(jsonPath("$.code").value("RANGE_NOT_SATISFIABLE"));
    }

    @Test
    void streamTrack_withUnknownTrack_returns404() throws Exception {
        when(trackRegistry.getTrack("missing")).thenThrow(new EntityNotFoundException("Track not found: missing"));

        mockMvc
            .perform(get("/api/stream/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.message").value("Track not found: missing"));
    }

    private Path copyFixture(String fileName) throws Exception {
        Path track = tempDir.resolve(fileName);
        try (InputStream input = new ClassPathResource("audio-library/" + fileName).getInputStream()) {
            Files.copy(input, track, StandardCopyOption.REPLACE_EXISTING);
        }
        return track;
    }

    private FileTrack fileTrack(String id, String format) {
        return new FileTrack(
                id,
                new TrackMetadata(java.util.Map.of()),
                null,
                TrackState.healthy(),
                Path.of(id + "." + format),
                format
        );
    }
}
