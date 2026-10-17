package com.gravifon.player;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gravifon.player.api.model.TrackResponse;
import com.gravifon.player.registry.model.Track;
import com.gravifon.player.registry.stream.StreamResolver;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "gravifon.library.startup-scan-"
        + "enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(StreamRegistrationWorkflowIntegrationTest.ResolverConfiguration.class)
class StreamRegistrationWorkflowIntegrationTest {
    private static final String SESSION_HEADER = "X-Client-Session";
    private static final String ALBUM_SOURCE = "https://resolver.example/album";
    private static final String SINGLE_SOURCE = "https://resolver.example/single";
    private static final String FIRST_TRACK_SOURCE = "https://media.example/first?utm_source=resolver";
    private static final String SECOND_TRACK_SOURCE = "https://media.example/second";
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registrationFansOutAndReusesTracksThroughTheApi() throws Exception {
        String sessionId = acquireSession();

        List<String> fanOutIds = register(sessionId, List.of(ALBUM_SOURCE, SINGLE_SOURCE));
        assertThat(fanOutIds).hasSize(3).first().isEqualTo(fanOutIds.getLast());
        assertThat(fanOutIds.get(0)).isNotEqualTo(fanOutIds.get(1));

        List<String> repeatedIds = register(sessionId, List.of(ALBUM_SOURCE));
        assertThat(repeatedIds).containsExactly(fanOutIds.get(0), fanOutIds.get(1));

        List<TrackResponse> registeredTracks = listTracks(sessionId);
        assertThat(registeredTracks)
            .extracting(TrackResponse::id)
            .containsExactlyInAnyOrderElementsOf(List.of(fanOutIds.get(0), fanOutIds.get(1)));
        assertThat(registeredTracks)
            .filteredOn(track -> track.id().equals(fanOutIds.get(0)))
            .singleElement()
            .satisfies(track -> assertThat(track.metadata().get("TITLE")).containsExactly("First track"));
    }

    private String acquireSession() throws Exception {
        String body = mockMvc
            .perform(post("/api/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"stream-registration-user\"}")
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper.readTree(body).get("sessionId").asText();
    }

    private List<String> register(String sessionId, List<String> sourceUrls) throws Exception {
        String body = mockMvc
            .perform(post("/api/tracks/streams")
                .header(SESSION_HEADER, sessionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("sourceUrls", sourceUrls)))
            )
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return objectMapper
            .readTree(body)
            .get("trackIds")
            .traverse(objectMapper)
            .readValueAs(new TypeReference<>() {
            });
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

    @TestConfiguration(proxyBeanMethods = false)
    static class ResolverConfiguration {
        @Bean
        StreamResolver fixtureResolver() {
            return new FixtureResolver();
        }
    }

    private static class FixtureResolver implements StreamResolver {
        @Override
        public boolean supports(String sourceUrl) {
            return ALBUM_SOURCE.equals(sourceUrl) || SINGLE_SOURCE.equals(sourceUrl);
        }

        @Override
        public List<ResolvedTrack> resolveTracks(String sourceUrl) {
            ResolvedTrack first = new ResolvedTrack(FIRST_TRACK_SOURCE, Map.of("TITLE", List.of("First track")), 100L);
            if (SINGLE_SOURCE.equals(sourceUrl)) {
                return List.of(first);
            }
            return List.of(
                    first,
                    new ResolvedTrack(SECOND_TRACK_SOURCE, Map.of("TITLE", List.of("Second track")), 110L)
            );
        }

        @Override
        public ResolvedStream refreshStream(Track track) {
            return new ResolvedStream("https://cdn.example/stream", Instant.EPOCH.plusSeconds(60));
        }
    }
}
