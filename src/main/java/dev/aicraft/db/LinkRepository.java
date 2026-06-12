package dev.aicraft.db;

import dev.aicraft.model.WebSession;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import java.util.UUID;

public final class LinkRepository {

    private final DatabaseManager database;

    public LinkRepository(DatabaseManager database) {
        this.database = database;
    }

    public void createLinkRequest(String username, long expiresAt) throws SQLException {
        expireOldRequests(username);
        String sql = "INSERT INTO link_requests (username, created_at, expires_at, consumed) VALUES (?, ?, ?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setLong(2, System.currentTimeMillis());
            statement.setLong(3, expiresAt);
            setConsumed(statement, 4, false);
            statement.executeUpdate();
        }
    }

    public Optional<Long> findPendingRequestId(String username) throws SQLException {
        String sql = """
                SELECT id FROM link_requests
                WHERE LOWER(username) = LOWER(?) AND consumed = ? AND expires_at > ? AND code IS NULL
                ORDER BY id DESC LIMIT 1
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            setConsumed(statement, 2, false);
            statement.setLong(3, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getLong(1));
                }
            }
        }
        return Optional.empty();
    }

    public void assignCode(long requestId, String code, UUID playerUuid) throws SQLException {
        String sql = "UPDATE link_requests SET code = ?, player_uuid = ? WHERE id = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, playerUuid.toString());
            statement.setLong(3, requestId);
            statement.executeUpdate();
        }
    }

    public Optional<UUID> verifyAndConsume(String username, String code) throws SQLException {
        String sql = """
                SELECT id, player_uuid FROM link_requests
                WHERE LOWER(username) = LOWER(?) AND code = ? AND consumed = ? AND expires_at > ?
                AND player_uuid IS NOT NULL
                ORDER BY id DESC LIMIT 1
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, code);
            setConsumed(statement, 3, false);
            statement.setLong(4, System.currentTimeMillis());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                UUID playerUuid = UUID.fromString(result.getString("player_uuid"));
                consumeRequest(result.getLong("id"));
                return Optional.of(playerUuid);
            }
        }
    }

    public void createSession(String token, UUID playerUuid, String username, long expiresAt) throws SQLException {
        String sql = "INSERT INTO web_sessions (token, player_uuid, username, created_at, expires_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            long now = System.currentTimeMillis();
            statement.setString(1, token);
            statement.setString(2, playerUuid.toString());
            statement.setString(3, username);
            statement.setLong(4, now);
            statement.setLong(5, expiresAt);
            statement.executeUpdate();
        }
    }

    public Optional<WebSession> findSession(String token) throws SQLException {
        String sql = "SELECT token, player_uuid, username, expires_at FROM web_sessions WHERE token = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(new WebSession(
                            result.getString("token"),
                            UUID.fromString(result.getString("player_uuid")),
                            result.getString("username"),
                            result.getLong("expires_at")
                    ));
                }
            }
        }
        return Optional.empty();
    }

    public void deleteSession(String token) throws SQLException {
        String sql = "DELETE FROM web_sessions WHERE token = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, token);
            statement.executeUpdate();
        }
    }

    private void consumeRequest(long requestId) throws SQLException {
        String sql = "UPDATE link_requests SET consumed = ? WHERE id = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setConsumed(statement, 1, true);
            statement.setLong(2, requestId);
            statement.executeUpdate();
        }
    }

    private void expireOldRequests(String username) throws SQLException {
        String sql = "UPDATE link_requests SET consumed = ? WHERE LOWER(username) = LOWER(?) AND consumed = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setConsumed(statement, 1, true);
            statement.setString(2, username);
            setConsumed(statement, 3, false);
            statement.executeUpdate();
        }
    }

    private void setConsumed(PreparedStatement statement, int index, boolean consumed) throws SQLException {
        if (database.isConsumedColumnBoolean()) {
            statement.setBoolean(index, consumed);
        } else {
            statement.setInt(index, consumed ? 1 : 0);
        }
    }
}
