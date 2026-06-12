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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class ChatService implements AutoCloseable {

    private final PluginConfig pluginConfig;
    private final ChatRepository chatRepository;
    private final ChatSessionManager sessionManager;
    private final RateLimitService rateLimitService;
    private final OpenAiCompatibleClient aiClient;
    private final ExecutorService dbExecutor;

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
        this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "aicraft-db");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<ChatRecord> startChat(UUID playerUuid, String username, ChatSource source) {
        return CompletableFuture.supplyAsync(() -> createChatRecord(playerUuid, username, source), dbExecutor)
                .thenApply(record -> {
                    sessionManager.start(playerUuid, record);
                    return record;
                });
    }

    public CompletableFuture<ChatRecord> reopenChat(UUID playerUuid, int playerChatNumber) {
        return CompletableFuture.supplyAsync(() -> loadChat(playerUuid, playerChatNumber), dbExecutor)
                .thenApply(loaded -> {
                    applySession(playerUuid, loaded);
                    return loaded.record();
                });
    }

    public CompletableFuture<ChatRecord> reopenChatById(UUID playerUuid, long chatId) {
        return CompletableFuture.supplyAsync(() -> db(() -> {
            ChatRecord record = chatRepository.findById(chatId)
                    .orElseThrow(() -> new ChatException("Chat not found."));
            if (!record.playerUuid().equals(playerUuid.toString())) {
                throw new ChatException("That chat does not belong to you.");
            }
            return loadChat(playerUuid, record.playerChatNumber());
        }), dbExecutor).thenApply(loaded -> {
            applySession(playerUuid, loaded);
            return loaded.record();
        });
    }

    public CompletableFuture<Void> endChat(UUID playerUuid) {
        Optional<ChatSession> active = sessionManager.get(playerUuid);
        if (active.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        long chatId = active.get().databaseChatId();
        return CompletableFuture.runAsync(() -> dbVoid(() -> chatRepository.endChat(chatId)), dbExecutor)
                .thenRun(() -> sessionManager.end(playerUuid));
    }

    public CompletableFuture<String> sendMessage(UUID playerUuid, String content, Consumer<String> onUserEcho) {
        ChatSession session = sessionManager.get(playerUuid)
                .orElseThrow(() -> new ChatException("No active chat. Start one with /newchat or reopen a chat."));
        session.addUserMessage(content);
        long chatId = session.databaseChatId();
        List<ChatMessage> messages = List.copyOf(session.messagesForRequest());
        if (onUserEcho != null) {
            onUserEcho.accept(content);
        }
        return CompletableFuture.runAsync(() ->
                        dbVoid(() -> chatRepository.addMessage(chatId, "user", content)), dbExecutor)
                .thenCompose(ignored -> aiClient.chatAsync(messages))
                .thenCompose(reply -> CompletableFuture.supplyAsync(() -> {
                    dbVoid(() -> chatRepository.addMessage(chatId, "assistant", reply));
                    return reply;
                }, dbExecutor))
                .thenApply(reply -> {
                    session.addAssistantMessage(reply);
                    return reply;
                });
    }

    public CompletableFuture<List<ChatRecord>> listChatsAsync(UUID playerUuid) {
        return CompletableFuture.supplyAsync(
                () -> db(() -> chatRepository.listChatsForPlayer(playerUuid)),
                dbExecutor
        );
    }

    public CompletableFuture<List<StoredMessage>> listMessagesAsync(UUID playerUuid, long chatId) {
        return CompletableFuture.supplyAsync(() -> db(() -> {
            ChatRecord record = chatRepository.findById(chatId)
                    .orElseThrow(() -> new ChatException("Chat not found."));
            if (!record.playerUuid().equals(playerUuid.toString())) {
                throw new ChatException("That chat does not belong to you.");
            }
            return chatRepository.listMessages(chatId);
        }), dbExecutor);
    }

    public Optional<ChatRecord> getActiveChat(UUID playerUuid) {
        return sessionManager.get(playerUuid).map(ChatSession::chatRecord);
    }

    public ChatSessionManager sessionManager() {
        return sessionManager;
    }

    public ExecutorService dbExecutor() {
        return dbExecutor;
    }

    @Override
    public void close() {
        dbExecutor.shutdownNow();
    }

    private ChatRecord createChatRecord(UUID playerUuid, String username, ChatSource source) {
        return db(() -> {
            rateLimitService.ensureCanCreateChat(playerUuid, username);
            int chatNumber = chatRepository.nextChatNumber(playerUuid);
            long chatId = chatRepository.createChat(playerUuid, chatNumber, source);
            if (!pluginConfig.systemPrompt().isBlank()) {
                chatRepository.addMessage(chatId, "system", pluginConfig.systemPrompt());
            }
            return chatRepository.findById(chatId).orElseThrow();
        });
    }

    private LoadedChat loadChat(UUID playerUuid, int playerChatNumber) {
        return db(() -> {
            ChatRecord record = chatRepository.findByPlayerAndNumber(playerUuid, playerChatNumber)
                    .orElseThrow(() -> new ChatException(
                            "Chat #" + playerChatNumber + " was not found for your account."));
            chatRepository.reactivateChat(record.id());
            ChatRecord refreshed = chatRepository.findById(record.id()).orElse(record);
            List<StoredMessage> messages = chatRepository.listMessages(refreshed.id());
            return new LoadedChat(refreshed, messages);
        });
    }

    private void applySession(UUID playerUuid, LoadedChat loaded) {
        ChatSession session = sessionManager.start(playerUuid, loaded.record());
        session.clearMessages();
        for (StoredMessage message : loaded.messages()) {
            session.addRawMessage(new ChatMessage(message.role(), message.content()));
        }
    }

    private static void dbVoid(SqlRunnable runnable) {
        try {
            runnable.run();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static <T> T db(SqlSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    private record LoadedChat(ChatRecord record, List<StoredMessage> messages) {
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
