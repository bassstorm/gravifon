package com.gravifon.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gravifon.player.api.model.TrackResponse;
import com.gravifon.player.observability.CorrelationIdFilter;
import com.gravifon.player.registry.service.TrackRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "gravifon.library.startup-scan-"
        + "enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GravifonIntegrationTest {
    private static final List<String> FIXTURES = List.of("amber-signal.mp3", "blue-current.flac", "green-air.ogg");
    private static final Map<String, ExpectedMetadata> EXPECTED_METADATA = Map.of(
            "amber-signal.mp3",
            new ExpectedMetadata("Amber Signal", "Fiona Vale", "Shared Scenes", 1),
            "blue-current.flac",
            new ExpectedMetadata("Blue Current", "Jun Park", "Shared Scenes", 0),
            "green-air.ogg",
            new ExpectedMetadata("Green Air", "Fiona Vale", "Open Rooms", 0)
    );
    @TempDir
    static Path musicRoot;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("gravifon.library.dir", () -> musicRoot.toString());
    }

    @BeforeAll
    static void prepareMusicRoot() throws IOException {
        restoreFixtures();
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TrackRegistry trackRegistry;
    private String sessionId;

    @BeforeEach
    void refreshCatalog() throws Exception {
        restoreFixtures();
        trackRegistry.refresh();
        sessionId = acquireSession("integration-user");
    }

    @Test
    void tracks_listIncludesTaggedAudioInEverySupportedFormat() throws Exception {
        List<TrackResponse> tracks = listTracks();

        assertThat(tracks).hasSize(3);
        assertThat(tracks).extracting(TrackResponse::format).containsExactlyInAnyOrder("mp3", "flac", "ogg");
        for (TrackResponse track : tracks) {
            ExpectedMetadata expected = EXPECTED_METADATA.get(track.filename());
            assertThat(expected).isNotNull();
            assertThat(track.metadata().get("TITLE")).singleElement().asString().contains(expected.title());
            assertThat(track.metadata().get("ARTIST")).singleElement().asString().contains(expected.artist());
            assertThat(track.metadata().get("ALBUM")).singleElement().asString().contains(expected.album());
            assertThat(track.durationSeconds()).isNotNull();
            assertThat(track.durationSeconds())
                .isBetween(3L - expected.durationToleranceSeconds(), 3L + expected.durationToleranceSeconds());
        }

        mockMvc
            .perform(get("/api/tracks")
                .header("X-Client-Session", sessionId)
                .header(CorrelationIdFilter.CORRELATION_ID_HEADER, "it-cid-001")
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(header().string(CorrelationIdFilter.CORRELATION_ID_HEADER, "it-cid-001"));
    }

    @Test
    void tracks_unknownTrackReturns404() throws Exception {
        mockMvc
            .perform(get("/api/tracks/nonexistent")
                .header("X-Client-Session", sessionId)
                .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isNotFound());
    }

    @Test
    void stream_fullFileAndRangeRequestsForEveryFormat() throws Exception {
        for (TrackResponse track : listTracks()) {
            byte[] expectedBytes = Files.readAllBytes(musicRoot.resolve(track.filename()));
            mockMvc
                .perform(get("/api/stream/" + track.id()).header("X-Client-Session", sessionId))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, Integer.toString(expectedBytes.length)))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                    .containsExactly(expectedBytes));

            int rangeEnd = Math.min(expectedBytes.length - 1, 31);
            mockMvc
                .perform(get("/api/stream/" + track.id())
                    .header("X-Client-Session", sessionId)
                    .header(HttpHeaders.RANGE, "bytes=7-" + rangeEnd)
                )
                .andExpect(status().isPartialContent())
                .andExpect(header()
                    .string(HttpHeaders.CONTENT_RANGE, "bytes 7-" + rangeEnd + "/" + expectedBytes.length)
                )
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                    .containsExactly(Arrays.copyOfRange(expectedBytes, 7, rangeEnd + 1)));
        }
    }

    private List<TrackResponse> listTracks() throws Exception {
        String body = mockMvc
            .perform(get("/api/tracks").header("X-Client-Session", sessionId).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readValue(body, new TypeReference<>() {
        });
    }

    private String acquireSession(String username) throws Exception {
        String response = mockMvc
            .perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username)))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(response).get("sessionId").asText();
    }

    private static void restoreFixtures() throws IOException {
        Files.createDirectories(musicRoot);
        for (String filename : FIXTURES) {
            ClassPathResource fixture = new ClassPathResource("audio-library/" + filename);
            try (InputStream input = fixture.getInputStream()) {
                Files.copy(input, musicRoot.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private record ExpectedMetadata(String title, String artist, String album, long durationToleranceSeconds) {}
}
