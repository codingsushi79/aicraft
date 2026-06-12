package dev.aicraft.model;

public record StoredMessage(long id, long chatId, String role, String content, long createdAt) {
}
