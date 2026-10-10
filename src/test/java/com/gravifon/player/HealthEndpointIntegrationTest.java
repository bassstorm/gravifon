package com.gravifon.player;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gravifon.player.registry.scan.ScanOutcome;
import com.gravifon.player.registry.scan.ScanOutcomeTracker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "gravifon.library.startup-scan-"
        + "enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthEndpointIntegrationTest {
    @TempDir
    static Path musicRoot;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("gravifon.library.music-root", () -> musicRoot.toString());
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ScanOutcomeTracker scanOutcomeTracker;

    @Test
    void healthReadinessAndLivenessExposeTheConfiguredContributors() throws Exception {
        Files.createDirectories(musicRoot);

        mockMvc
            .perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.db.status").value("UP"))
            .andExpect(jsonPath("$.components.musicLibrary.status").value("UP"))
            .andExpect(jsonPath("$.components.libraryScan.status").value("UP"));

        mockMvc
            .perform(get("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.components.db.status").value("UP"))
            .andExpect(jsonPath("$.components.musicLibrary.status").value("UP"))
            .andExpect(jsonPath("$.components.livenessState").doesNotExist())
            .andExpect(jsonPath("$.components.libraryScan").doesNotExist())
            .andExpect(jsonPath("$.components.length()").value(2));

        mockMvc
            .perform(get("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.livenessState.status").value("UP"))
            .andExpect(jsonPath("$.components.db").doesNotExist())
            .andExpect(jsonPath("$.components.musicLibrary").doesNotExist())
            .andExpect(jsonPath("$.components.length()").value(1));

        Instant completedAt = Instant.parse("2026-01-01T00:00:00Z");
        scanOutcomeTracker.record(ScanOutcome.success(completedAt));
        mockMvc
            .perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.libraryScan.details.success").value(true));

        scanOutcomeTracker.record(ScanOutcome.failure(completedAt, "permission denied"));
        mockMvc
            .perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components.libraryScan.status").value("UP"))
            .andExpect(jsonPath("$.components.libraryScan.details.success").value(false))
            .andExpect(jsonPath("$.components.libraryScan.details.error").value("permission denied"));
    }
}
