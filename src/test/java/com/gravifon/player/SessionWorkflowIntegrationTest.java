package com.gravifon.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gravifon.player.api.model.TrackResponse;
import com.gravifon.player.registry.service.TrackRegistry;
import com.gravifon.player.session.model.ClientSessionId;
import com.gravifon.player.session.repository.ClientSessionRepository;
import com.gravifon.player.session.repository.UserRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "gravifon.library.startup-scan-"
        + "enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SessionWorkflowIntegrationTest {
    private static final List<String> FIXTURES = List.of("amber-signal.mp3", "blue-current.flac", "green-air.ogg");
    private static final String SESSION_HEADER = "X-Client-Session";
    @TempDir
    static Path musicRoot;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("gravifon.library.music-root", () -> musicRoot.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TrackRegistry trackRegistry;
    @Autowired
    private ClientSessionRepository sessions;
    @Autowired
    private UserRepository users;
    private final Set<String> createdSessionIds = new HashSet<>();
    private final Set<String> createdUserNames = new HashSet<>();

    @BeforeEach
    void refreshCatalogAndResetRollbackAssertions() throws IOException {
        createdSessionIds.clear();
        createdUserNames.clear();
        restoreFixtures();
        trackRegistry.refresh();
    }

    @Test
    void acquisitionTracksAudioInvalidationAndReacquisitionAreIsolatedAndRollback() throws Exception {
        Map<String, byte[]> checkedInFixtures = readSourceFixtures();
        AcquiredSession first = acquireSession("WorkflowUser");
        List<TrackResponse> tracks = listTracks(first.sessionId());

        assertThat(tracks).hasSize(3);
        TrackResponse track = tracks.getFirst();
        byte[] expectedBytes = Files.readAllBytes(musicRoot.resolve(track.filename()));

        mockMvc
            .perform(get("/api/stream/" + track.id()).header(SESSION_HEADER, first.sessionId()))
            .andExpect(status().isOk())
            .andExpect(content().bytes(expectedBytes));
        int rangeEnd = Math.min(expectedBytes.length - 1, 31);
        mockMvc
            .perform(get("/api/stream/" + track.id())
                .header(SESSION_HEADER, first.sessionId())
                .header(HttpHeaders.RANGE, "bytes=7-" + rangeEnd)
            )
            .andExpect(status().isPartialContent())
            .andExpect(content().bytes(Arrays.copyOfRange(expectedBytes, 7, rangeEnd + 1)));

        AcquiredSession sameUser = acquireSession(" workflowuser ");
        AcquiredSession differentUser = acquireSession("another-user");
        assertThat(sameUser.userId()).isEqualTo(first.userId());
        assertThat(sameUser.sessionId()).isNotEqualTo(first.sessionId());
        assertThat(differentUser.userId()).isNotEqualTo(first.userId());

        mockMvc
            .perform(delete("/api/sessions/current").header(SESSION_HEADER, first.sessionId()))
            .andExpect(status().isNoContent());
        mockMvc
            .perform(get("/api/tracks").header(SESSION_HEADER, first.sessionId()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("SESSION_REQUIRED"));
        mockMvc.perform(get("/api/tracks").header(SESSION_HEADER, sameUser.sessionId())).andExpect(status().isOk());

        AcquiredSession reacquired = acquireSession("workflowuser");
        assertThat(reacquired.userId()).isEqualTo(first.userId());
        assertThat(reacquired.sessionId()).isNotEqualTo(first.sessionId());
        assertThat(listTracks(reacquired.sessionId())).hasSize(3);

        for (String filename : FIXTURES) {
            assertThat(Files.readAllBytes(musicRoot.resolve(filename))).containsExactly(checkedInFixtures.get(filename));
            assertThat(readSourceFixture(filename)).containsExactly(checkedInFixtures.get(filename));
        }
    }

    @AfterTransaction
    void databaseWritesAreRolledBack() {
        assertThat(trackRegistry.listTracks()).isEmpty();
        for (String sessionId : createdSessionIds) {
            assertThat(sessions.findById(ClientSessionId.of(sessionId))).isEmpty();
        }
        for (String normalizedName : createdUserNames) {
            assertThat(users.findByNormalizedName(normalizedName)).isEmpty();
        }
    }

    private AcquiredSession acquireSession(String username) throws Exception {
        String response = mockMvc
            .perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username)))
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        var body = objectMapper.readTree(response);
        String sessionId = body.get("sessionId").asText();
        String userId = body.get("userId").asText();
        createdSessionIds.add(sessionId);
        createdUserNames.add(username.strip().toLowerCase(Locale.ROOT));
        return new AcquiredSession(sessionId, userId);
    }

    private List<TrackResponse> listTracks(String sessionId) throws Exception {
        String body = mockMvc
            .perform(get("/api/tracks").header(SESSION_HEADER, sessionId).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readValue(body, new TypeReference<>() {
        });
    }

    private static void restoreFixtures() throws IOException {
        Files.createDirectories(musicRoot);
        for (String filename : FIXTURES) {
            try (InputStream input = new ClassPathResource("audio-library/" + filename).getInputStream()) {
                Files.copy(input, musicRoot.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Map<String, byte[]> readSourceFixtures() throws IOException {
        Map<String, byte[]> fixtures = new HashMap<>();
        for (String filename : FIXTURES) {
            fixtures.put(filename, readSourceFixture(filename));
        }
        return fixtures;
    }

    private static byte[] readSourceFixture(String filename) throws IOException {
        try (InputStream input = new ClassPathResource("audio-library/" + filename).getInputStream()) {
            return input.readAllBytes();
        }
    }

    private record AcquiredSession(String sessionId, String userId) {}
}
