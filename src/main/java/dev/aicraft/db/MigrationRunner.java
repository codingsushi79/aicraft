package dev.aicraft.db;

import dev.aicraft.config.WebConfig;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;
import java.util.stream.Collectors;

final class MigrationRunner {

    private static final int TARGET_VERSION = 1;

    private final WebConfig.DatabaseConfig.DatabaseType type;
    private final Logger logger;

    MigrationRunner(WebConfig.DatabaseConfig.DatabaseType type, Logger logger) {
        this.type = type;
        this.logger = logger;
    }

    void migrate(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            int current = readVersion(connection);
            if (current >= TARGET_VERSION) {
                return;
            }
            for (int version = current + 1; version <= TARGET_VERSION; version++) {
                applyMigration(connection, version);
                writeVersion(connection, version);
                logger.info("Applied database migration V" + version);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Database migration failed", e);
        }
    }

    private int readVersion(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)");
            try (ResultSet result = statement.executeQuery("SELECT version FROM schema_version LIMIT 1")) {
                if (result.next()) {
                    return result.getInt(1);
                }
            }
            statement.execute("INSERT INTO schema_version (version) VALUES (0)");
            return 0;
        }
    }

    private void writeVersion(Connection connection, int version) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM schema_version");
            statement.execute("INSERT INTO schema_version (version) VALUES (" + version + ")");
        }
    }

    private void applyMigration(Connection connection, int version) throws Exception {
        String suffix = type == WebConfig.DatabaseConfig.DatabaseType.POSTGRES ? "postgres" : "sqlite";
        String path = "/db/migration/V" + version + "__init_" + suffix + ".sql";
        try (InputStream stream = MigrationRunner.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing migration resource: " + path);
            }
            String sql = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .collect(Collectors.joining("\n"));
            for (String statement : sql.split(";")) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty()) {
                    try (Statement exec = connection.createStatement()) {
                        exec.execute(trimmed);
                    }
                }
            }
        }
    }
}
