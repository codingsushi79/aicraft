package dev.aicraft.ai;

import dev.aicraft.config.PluginConfig;
import dev.aicraft.model.ChatRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ChatSession {

    private final UUID playerId;
    private final ChatRecord chatRecord;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final PluginConfig config;

    public ChatSession(UUID playerId, ChatRecord chatRecord, PluginConfig config) {
        this.playerId = playerId;
        this.chatRecord = chatRecord;
        this.config = config;
    }

    public UUID playerId() {
        return playerId;
    }

    public ChatRecord chatRecord() {
        return chatRecord;
    }

    public long databaseChatId() {
        return chatRecord.id();
    }

    public int playerChatNumber() {
        return chatRecord.playerChatNumber();
    }

    public List<ChatMessage> messagesForRequest() {
        return Collections.unmodifiableList(messages);
    }

    public void clearMessages() {
        messages.clear();
    }

    public void addRawMessage(ChatMessage message) {
        messages.add(message);
    }

    public void addUserMessage(String content) {
        messages.add(new ChatMessage("user", content));
        trimHistory();
    }

    public void addAssistantMessage(String content) {
        messages.add(new ChatMessage("assistant", content));
        trimHistory();
    }

    private void trimHistory() {
        int max = config.maxHistoryMessages();
        if (max <= 0) {
            return;
        }

        boolean hasSystem = !messages.isEmpty() && "system".equals(messages.getFirst().role());
        int start = hasSystem ? 1 : 0;
        int conversational = messages.size() - start;
        if (conversational <= max) {
            return;
        }

        int remove = conversational - max;
        messages.subList(start, start + remove).clear();
    }
}
