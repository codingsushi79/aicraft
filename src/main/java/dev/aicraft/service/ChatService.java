package dev.aicraft.service;

import dev.aicraft.ai.ChatMessage;
import dev.aicraft.ai.ChatSession;
import dev.aicraft.ai.ChatSessionManager;
import dev.aicraft.ai.OpenAiCompatibleClient;
import dev.aicraft.config.PluginConfig;
import dev.aicraft.db.ChatRepository;
import dev.aicraft.model.ChatRecord;
import dev.aicraft.model.ChatSource;
import dev.aicraft.model.StoredMessage;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class ChatService {

    private final PluginConfig pluginConfig;
    private final ChatRepository chatRepository;
    private final ChatSessionManager sessionManager;
    private final RateLimitService rateLimitService;
    private final OpenAiCompatibleClient aiClient;

    public ChatService(
            PluginConfig pluginConfig,
            ChatRepository chatRepository,
            ChatSessionManager sessionManager,
            RateLimitService rateLimitService,
            OpenAiCompatibleClient aiClient
    ) {
        this.pluginConfig = pluginConfig;
        this.chatRepository = chatRepository;
        this.sessionManager = sessionManager;
        this.rateLimitService = rateLimitService;
        this.aiClient = aiClient;
    }

    public ChatRecord startChat(UUID playerUuid, String username, ChatSource source) {
        return db(() -> {
            rateLimitService.ensureCanCreateChat(playerUuid, username);
            int chatNumber = chatRepository.nextChatNumber(playerUuid);
            long chatId = chatRepository.createChat(playerUuid, chatNumber, source);
            if (!pluginConfig.systemPrompt().isBlank()) {
                chatRepository.addMessage(chatId, "system", pluginConfig.systemPrompt());
            }
            ChatRecord record = chatRepository.findById(chatId).orElseThrow();
            sessionManager.start(playerUuid, record);
            return record;
        });
    }

    public ChatRecord reopenChatById(UUID playerUuid, long chatId) {
        return db(() -> {
            ChatRecord record = chatRepository.findById(chatId)
                    .orElseThrow(() -> new ChatException("Chat not found."));
            if (!record.playerUuid().equals(playerUuid.toString())) {
                throw new ChatException("That chat does not belong to you.");
            }
            return reopenChat(playerUuid, record.playerChatNumber());
        });
    }

    public ChatRecord reopenChat(UUID playerUuid, int playerChatNumber) {
        return db(() -> {
            ChatRecord record = chatRepository.findByPlayerAndNumber(playerUuid, playerChatNumber)
                    .orElseThrow(() -> new ChatException(
                            "Chat #" + playerChatNumber + " was not found for your account."));
            List<StoredMessage> stored = chatRepository.listMessages(record.id());
            ChatSession session = sessionManager.start(playerUuid, record);
            session.clearMessages();
            for (StoredMessage message : stored) {
                session.addRawMessage(new ChatMessage(message.role(), message.content()));
            }
            chatRepository.reactivateChat(record.id());
            return chatRepository.findById(record.id()).orElse(record);
        });
    }

    public void endChat(UUID playerUuid) {
        db(() -> {
            Optional<ChatSession> active = sessionManager.get(playerUuid);
            if (active.isPresent()) {
                chatRepository.endChat(active.get().databaseChatId());
            }
            sessionManager.end(playerUuid);
            return null;
        });
    }

    public CompletableFuture<String> sendMessage(UUID playerUuid, String content, Consumer<String> onUserEcho) {
        ChatSession session = sessionManager.get(playerUuid)
                .orElseThrow(() -> new ChatException("No active chat. Start one with /newchat or reopen a chat."));
        session.addUserMessage(content);
        db(() -> {
            chatRepository.addMessage(session.databaseChatId(), "user", content);
            return null;
        });
        if (onUserEcho != null) {
            onUserEcho.accept(content);
        }
        return aiClient.chatAsync(session.messagesForRequest()).thenApply(reply -> {
            session.addAssistantMessage(reply);
            db(() -> {
                chatRepository.addMessage(session.databaseChatId(), "assistant", reply);
                return null;
            });
            return reply;
        });
    }

    public List<ChatRecord> listChats(UUID playerUuid) {
        return db(() -> chatRepository.listChatsForPlayer(playerUuid));
    }

    public List<StoredMessage> listMessages(UUID playerUuid, long chatId) {
        return db(() -> {
            ChatRecord record = chatRepository.findById(chatId)
                    .orElseThrow(() -> new ChatException("Chat not found."));
            if (!record.playerUuid().equals(playerUuid.toString())) {
                throw new ChatException("That chat does not belong to you.");
            }
            return chatRepository.listMessages(chatId);
        });
    }

    public Optional<ChatRecord> getActiveChat(UUID playerUuid) {
        return sessionManager.get(playerUuid).map(ChatSession::chatRecord);
    }

    public ChatSessionManager sessionManager() {
        return sessionManager;
    }

    private static <T> T db(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    public static final class ChatException extends RuntimeException {
        public ChatException(String message) {
            super(message);
        }
    }
}
