package com.gravifon.player.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

class FlywayMigrationTest {
    @Test
    void migrationsApplyToEmptyDatabaseAndAreRepeatable() throws Exception {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
        try {
            Flyway flyway = Flyway.configure().dataSource(database).locations("classpath:db/migration").load();

            flyway.migrate();
            flyway.migrate();

            try (var connection = database.getConnection();
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
            try (var connection = database.getConnection();
                    var statement = connection.createStatement();
                    var result = statement.executeQuery(
                            "SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"version\" = '1' AND \"success\" = TRUE"
            )
            ) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(1);
            }
        } finally {
            database.shutdown();
        }
    }
}
