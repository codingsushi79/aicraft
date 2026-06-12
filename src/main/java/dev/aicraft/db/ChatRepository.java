package dev.aicraft.db;

import dev.aicraft.model.ChatRecord;
import dev.aicraft.model.ChatSource;
import dev.aicraft.model.StoredMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ChatRepository {

    private final DatabaseManager database;

    public ChatRepository(DatabaseManager database) {
        this.database = database;
    }

    public int nextChatNumber(UUID playerUuid) throws SQLException {
        String sql = "SELECT COALESCE(MAX(player_chat_number), 0) + 1 FROM chats WHERE player_uuid = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public long createChat(UUID playerUuid, int playerChatNumber, ChatSource source) throws SQLException {
        long now = System.currentTimeMillis();
        String sql = """
                INSERT INTO chats (player_uuid, player_chat_number, source, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, playerChatNumber);
            statement.setString(3, source.name());
            statement.setLong(4, now);
            statement.setLong(5, now);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("Failed to create chat");
    }

    public void touchChat(long chatId) throws SQLException {
        String sql = "UPDATE chats SET updated_at = ? WHERE id = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, System.currentTimeMillis());
            statement.setLong(2, chatId);
            statement.executeUpdate();
        }
    }

    public void reactivateChat(long chatId) throws SQLException {
        long now = System.currentTimeMillis();
        String sql = "UPDATE chats SET ended_at = NULL, updated_at = ? WHERE id = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setLong(2, chatId);
            statement.executeUpdate();
        }
    }

    public void endChat(long chatId) throws SQLException {
        long now = System.currentTimeMillis();
        String sql = "UPDATE chats SET ended_at = ?, updated_at = ? WHERE id = ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, now);
            statement.setLong(2, now);
            statement.setLong(3, chatId);
            statement.executeUpdate();
        }
    }

    public void addMessage(long chatId, String role, String content) throws SQLException {
        String sql = "INSERT INTO chat_messages (chat_id, role, content, created_at) VALUES (?, ?, ?, ?)";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            statement.setString(2, role);
            statement.setString(3, content);
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        }
        touchChat(chatId);
    }

    public int countChatsCreatedSince(UUID playerUuid, long sinceMillis) throws SQLException {
        String sql = "SELECT COUNT(*) FROM chats WHERE player_uuid = ? AND created_at >= ?";
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setLong(2, sinceMillis);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public Optional<ChatRecord> findByPlayerAndNumber(UUID playerUuid, int playerChatNumber) throws SQLException {
        String sql = """
                SELECT id, player_uuid, player_chat_number, source, created_at, updated_at, ended_at
                FROM chats WHERE player_uuid = ? AND player_chat_number = ?
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            statement.setInt(2, playerChatNumber);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapChat(result));
                }
            }
        }
        return Optional.empty();
    }

    public Optional<ChatRecord> findById(long chatId) throws SQLException {
        String sql = """
                SELECT id, player_uuid, player_chat_number, source, created_at, updated_at, ended_at
                FROM chats WHERE id = ?
                """;
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(mapChat(result));
                }
            }
        }
        return Optional.empty();
    }

    public List<ChatRecord> listChatsForPlayer(UUID playerUuid) throws SQLException {
        String sql = """
                SELECT id, player_uuid, player_chat_number, source, created_at, updated_at, ended_at
                FROM chats WHERE player_uuid = ? ORDER BY player_chat_number DESC
                """;
        List<ChatRecord> chats = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, playerUuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    chats.add(mapChat(result));
                }
            }
        }
        return chats;
    }

    public List<StoredMessage> listMessages(long chatId) throws SQLException {
        String sql = """
                SELECT id, chat_id, role, content, created_at
                FROM chat_messages WHERE chat_id = ? ORDER BY id ASC
                """;
        List<StoredMessage> messages = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, chatId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    messages.add(new StoredMessage(
                            result.getLong("id"),
                            result.getLong("chat_id"),
                            result.getString("role"),
                            result.getString("content"),
                            result.getLong("created_at")
                    ));
                }
            }
        }
        return messages;
    }

    private static ChatRecord mapChat(ResultSet result) throws SQLException {
        long endedAt = result.getLong("ended_at");
        return new ChatRecord(
                result.getLong("id"),
                result.getString("player_uuid"),
                result.getInt("player_chat_number"),
                ChatSource.fromString(result.getString("source")),
                result.getLong("created_at"),
                result.getLong("updated_at"),
                result.wasNull() ? null : endedAt
        );
    }
}
