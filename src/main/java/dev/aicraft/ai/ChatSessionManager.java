package dev.aicraft.ai;

import dev.aicraft.config.PluginConfig;
import dev.aicraft.model.ChatRecord;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatSessionManager {

    private final PluginConfig config;
    private final Map<UUID, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatSessionManager(PluginConfig config) {
        this.config = config;
    }

    public boolean hasSession(UUID playerId) {
        return sessions.containsKey(playerId);
    }

    public Optional<ChatSession> get(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    public ChatSession start(UUID playerId, ChatRecord chatRecord) {
        ChatSession session = new ChatSession(playerId, chatRecord, config);
        sessions.put(playerId, session);
        return session;
    }

    public void end(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clearAll() {
        sessions.clear();
    }
}
