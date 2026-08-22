package com.gravifon.player.config;

import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.SQLException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class SqliteDataSourceConfiguration {

    @Bean
    public DataSource dataSource(GravifonProperties properties) throws IOException {
        Files.createDirectories(properties.getConfigDir());

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl("jdbc:sqlite:%s".formatted(properties.getConfigDir().resolve("gravifon.db")));
        dataSource.setDriverClassName("org.sqlite.JDBC");
        dataSource.setConnectionInitSql("PRAGMA foreign_keys=ON");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
        } catch (SQLException exception) {
            dataSource.close();
            throw new IllegalStateException("Failed to configure SQLite WAL mode", exception);
        }
        return dataSource;
    }
}