package dev.aicraft.service;

import dev.aicraft.config.WebConfig;
import dev.aicraft.db.LinkRepository;
import dev.aicraft.model.WebSession;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public final class LinkService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebConfig webConfig;
    private final LinkRepository linkRepository;
    private final ExecutorService dbExecutor;

    public LinkService(WebConfig webConfig, LinkRepository linkRepository, ExecutorService dbExecutor) {
        this.webConfig = webConfig;
        this.linkRepository = linkRepository;
        this.dbExecutor = dbExecutor;
    }

    public CompletableFuture<Void> startLinkRequest(String username) {
        return CompletableFuture.runAsync(() -> db(() -> {
            long expiresAt = System.currentTimeMillis()
                    + TimeUnit.MINUTES.toMillis(webConfig.linkCodeExpiryMinutes());
            linkRepository.createLinkRequest(username.trim(), expiresAt);
        }), dbExecutor);
    }

    public CompletableFuture<Optional<String>> issueCodeForPlayer(String playerName, UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> db(() -> {
            Optional<Long> requestId = linkRepository.findPendingRequestId(playerName);
            if (requestId.isEmpty()) {
                return Optional.<String>empty();
            }
            String code = randomCode();
            linkRepository.assignCode(requestId.get(), code, playerUuid);
            return Optional.of(code);
        }), dbExecutor);
    }

    public CompletableFuture<WebSession> confirmLink(String username, String code) {
        return CompletableFuture.supplyAsync(() -> db(() -> {
            String normalizedCode = code.trim().toUpperCase();
            UUID playerUuid = linkRepository.verifyAndConsume(username, normalizedCode)
                    .orElseThrow(() -> new LinkException("Invalid or expired link code."));
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUuid);
            String resolvedName = offlinePlayer.getName() != null ? offlinePlayer.getName() : username;
            String token = UUID.randomUUID().toString();
            long expiresAt = System.currentTimeMillis()
                    + TimeUnit.DAYS.toMillis(webConfig.webSessionDays());
            linkRepository.createSession(token, playerUuid, resolvedName, expiresAt);
            return new WebSession(token, playerUuid, resolvedName, expiresAt);
        }), dbExecutor);
    }

    public CompletableFuture<Optional<WebSession>> findSession(String token) {
        return CompletableFuture.supplyAsync(() -> db(() -> {
            Optional<WebSession> session = linkRepository.findSession(token);
            if (session.isPresent() && session.get().isExpired()) {
                linkRepository.deleteSession(token);
                return Optional.empty();
            }
            return session;
        }), dbExecutor);
    }

    public CompletableFuture<Void> logout(String token) {
        return CompletableFuture.runAsync(() -> db(() -> linkRepository.deleteSession(token)), dbExecutor);
    }

    private static void db(SqlRunnable runnable) {
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

    private static String randomCode() {
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            builder.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return builder.toString();
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface SqlSupplier<T> {
        T get() throws SQLException;
    }

    public static final class LinkException extends RuntimeException {
        public LinkException(String message) {
            super(message);
        }
    }
}
