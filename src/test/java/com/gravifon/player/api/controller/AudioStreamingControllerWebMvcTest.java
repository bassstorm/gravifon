package com.gravifon.player.api.controller;

import com.gravifon.player.api.error.ApiExceptionHandler;
import com.gravifon.player.catalog.service.MediaCatalogService;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.playback.service.PlaybackService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AudioStreamingController.class)
@Import({ApiExceptionHandler.class, CorrelationIdFilter.class})
class AudioStreamingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaCatalogService mediaCatalogService;

    @MockitoBean
    private PlaybackService playbackService;

    @TempDir
    Path tempDir;

    @Test
    void streamTrack_withoutRange_returnsWholeResource() throws Exception {
        Path track = createTrackFile("track.mp3", "0123456789");
        when(mediaCatalogService.resolveTrackPath("t1")).thenReturn(Optional.of(track));

        mockMvc.perform(get("/api/stream/t1").accept(MediaType.ALL))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/mpeg"))
                .andExpect(content().bytes("0123456789".getBytes()));

            org.mockito.Mockito.verify(playbackService).observeStream("t1", 9, 10);
    }

    @Test
    void streamTrack_withClosedRange_returnsPartialContent() throws Exception {
        Path track = createTrackFile("track.mp3", "0123456789");
        when(mediaCatalogService.resolveTrackPath("t1")).thenReturn(Optional.of(track));

        mockMvc.perform(get("/api/stream/t1").header(HttpHeaders.RANGE, "bytes=2-5"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-5/10"))
                .andExpect(content().bytes("2345".getBytes()));

            org.mockito.Mockito.verify(playbackService).observeStream("t1", 5, 10);
    }

    @Test
    void streamTrack_withOpenEndedRange_returnsPartialContentToEnd() throws Exception {
        Path track = createTrackFile("track.ogg", "abcdefghij");
        when(mediaCatalogService.resolveTrackPath("t2")).thenReturn(Optional.of(track));

        mockMvc.perform(get("/api/stream/t2").header(HttpHeaders.RANGE, "bytes=6-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 6-9/10"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "audio/ogg"))
                .andExpect(content().bytes("ghij".getBytes()));
    }

    @Test
    void streamTrack_withUnsatisfiableRange_returns416() throws Exception {
        Path track = createTrackFile("track.flac", "abcdefghij");
        when(mediaCatalogService.resolveTrackPath("t3")).thenReturn(Optional.of(track));

        mockMvc.perform(get("/api/stream/t3").header(HttpHeaders.RANGE, "bytes=100-120"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(jsonPath("$.status").value(416));
    }

    @Test
    void streamTrack_withUnknownTrack_returns404() throws Exception {
        when(mediaCatalogService.resolveTrackPath("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/stream/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Track not found: missing"));
    }

    private Path createTrackFile(String fileName, String content) throws Exception {
        Path track = tempDir.resolve(fileName);
        Files.writeString(track, content);
        return track;
    }
}

