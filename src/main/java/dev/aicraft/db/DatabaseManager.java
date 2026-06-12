package dev.aicraft.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.aicraft.config.WebConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class DatabaseManager implements AutoCloseable {

    private final HikariDataSource dataSource;
    private final WebConfig.DatabaseConfig.DatabaseType type;
    private final Logger logger;

    public DatabaseManager(WebConfig.DatabaseConfig databaseConfig, File dataFolder, Logger logger) {
        this.type = databaseConfig.type();
        this.logger = logger;
        this.dataSource = createDataSource(databaseConfig, dataFolder);
        new MigrationRunner(type, logger).migrate(dataSource);
    }

    private static HikariDataSource createDataSource(WebConfig.DatabaseConfig config, File dataFolder) {
        HikariConfig hikari = new HikariConfig();
        if (config.type() == WebConfig.DatabaseConfig.DatabaseType.SQLITE) {
            File dbFile = new File(dataFolder, config.sqliteFile());
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hikari.setMaximumPoolSize(1);
        } else {
            hikari.setJdbcUrl("jdbc:postgresql://"
                    + config.postgresHost() + ":"
                    + config.postgresPort() + "/"
                    + config.postgresDatabase());
            hikari.setUsername(config.postgresUsername());
            hikari.setPassword(config.postgresPassword());
            hikari.setMaximumPoolSize(10);
        }
        hikari.setPoolName("aicraft-db");
        return new HikariDataSource(hikari);
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public WebConfig.DatabaseConfig.DatabaseType type() {
        return type;
    }

    public boolean isConsumedColumnBoolean() {
        return type == WebConfig.DatabaseConfig.DatabaseType.POSTGRES;
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
            logger.info("Database connection pool closed.");
        }
    }
}
