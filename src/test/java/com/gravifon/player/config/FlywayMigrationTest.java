package com.gravifon.player.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FlywayMigrationTest {
    @Test
    void migrationsApplyToEmptyDatabaseAndAreRepeatable(@TempDir Path tempDir) throws Exception {
        String url = "jdbc:sqlite:%s".formatted(tempDir.resolve("gravifon.db"));
        Flyway flyway = Flyway.configure().dataSource(url, null, null).locations("classpath:db/migration").load();

        flyway.migrate();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(url);
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type = 'table' AND name IN "
                        + "('track', 'track_metadata', 'playlist', 'playlist_entry', 'playback_state')"
        )
        ) {
            Set<String> tables = new HashSet<>();
            while (result.next()) {
                tables.add(result.getString(1));
            }
            assertThat(tables).containsExactlyInAnyOrder(
                    "track",
                    "track_metadata",
                    "playlist",
                    "playlist_entry",
                    "playback_state"
            );
        }
    }
}
