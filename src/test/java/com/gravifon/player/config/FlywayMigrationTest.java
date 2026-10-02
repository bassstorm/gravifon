package com.gravifon.player.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {
    @Test
    void migrationsApplyToEmptyDatabaseAndAreRepeatable() throws Exception {
        String url = "jdbc:h2:mem:gravifon-%s;DB_CLOSE_DELAY=-1".formatted(UUID.randomUUID());
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").load();

        flyway.migrate();
        flyway.migrate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'PUBLIC' "
                        + "AND TABLE_NAME IN ('TRACK', 'TRACK_METADATA', 'PLAYLIST', 'PLAYLIST_ENTRY', 'PLAYBACK_STATE')"
        )
        ) {
            Set<String> tables = new HashSet<>();
            while (result.next()) {
                tables.add(result.getString(1));
            }
            assertThat(tables).containsExactlyInAnyOrder(
                    "TRACK",
                    "TRACK_METADATA",
                    "PLAYLIST",
                    "PLAYLIST_ENTRY",
                    "PLAYBACK_STATE"
            );
        }
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var result = statement.executeQuery(
                        "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE"
        )
        ) {
            assertThat(result.next()).isTrue();
            assertThat(result.getInt(1)).isEqualTo(1);
        }
    }
}
