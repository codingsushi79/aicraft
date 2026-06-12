package dev.aicraft.config;

import org.bukkit.configuration.file.FileConfiguration;

public record WebConfig(
        boolean enabled,
        int port,
        String bindAddress,
        DatabaseConfig database,
        int defaultChatsPerDay,
        int linkCodeExpiryMinutes,
        int webSessionDays
) {
    public record DatabaseConfig(
            DatabaseType type,
            String sqliteFile,
            String postgresHost,
            int postgresPort,
            String postgresDatabase,
            String postgresUsername,
            String postgresPassword
    ) {
        public enum DatabaseType {
            SQLITE,
            POSTGRES
        }
    }

    public static WebConfig from(FileConfiguration config) {
        String dbType = config.getString("database.type", "sqlite");
        DatabaseConfig.DatabaseType type = "postgres".equalsIgnoreCase(dbType)
                ? DatabaseConfig.DatabaseType.POSTGRES
                : DatabaseConfig.DatabaseType.SQLITE;

        return new WebConfig(
                config.getBoolean("enabled", true),
                config.getInt("port", 8765),
                config.getString("bind-address", "0.0.0.0"),
                new DatabaseConfig(
                        type,
                        config.getString("database.sqlite-file", "aicraft.db"),
                        config.getString("database.postgres.host", "localhost"),
                        config.getInt("database.postgres.port", 5432),
                        config.getString("database.postgres.database", "aicraft"),
                        config.getString("database.postgres.username", "aicraft"),
                        config.getString("database.postgres.password", "")
                ),
                config.getInt("rate-limits.default-chats-per-day", 10),
                config.getInt("linking.link-code-expiry-minutes", 10),
                config.getInt("linking.web-session-days", 30)
        );
    }
}
