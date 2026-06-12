package dev.aicraft.model;

public record ChatRecord(
        long id,
        String playerUuid,
        int playerChatNumber,
        ChatSource source,
        long createdAt,
        long updatedAt,
        Long endedAt
) {
    public boolean isActive() {
        return endedAt == null;
    }
}
