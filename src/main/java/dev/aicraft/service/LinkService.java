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
import java.util.concurrent.TimeUnit;

public final class LinkService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final WebConfig webConfig;
    private final LinkRepository linkRepository;

    public LinkService(WebConfig webConfig, LinkRepository linkRepository) {
        this.webConfig = webConfig;
        this.linkRepository = linkRepository;
    }

    public void startLinkRequest(String username) throws SQLException {
        long expiresAt = System.currentTimeMillis()
                + TimeUnit.MINUTES.toMillis(webConfig.linkCodeExpiryMinutes());
        linkRepository.createLinkRequest(username.trim(), expiresAt);
    }

    public Optional<String> issueCodeForPlayer(String playerName, UUID playerUuid) throws SQLException {
        Optional<Long> requestId = linkRepository.findPendingRequestId(playerName);
        if (requestId.isEmpty()) {
            return Optional.empty();
        }
        String code = randomCode();
        linkRepository.assignCode(requestId.get(), code, playerUuid);
        return Optional.of(code);
    }

    public WebSession confirmLink(String username, String code) throws SQLException {
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
    }

    public Optional<WebSession> findSession(String token) throws SQLException {
        Optional<WebSession> session = linkRepository.findSession(token);
        if (session.isPresent() && session.get().isExpired()) {
            linkRepository.deleteSession(token);
            return Optional.empty();
        }
        return session;
    }

    public void logout(String token) throws SQLException {
        linkRepository.deleteSession(token);
    }

    private static String randomCode() {
        StringBuilder builder = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            builder.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
        }
        return builder.toString();
    }

    public static final class LinkException extends RuntimeException {
        public LinkException(String message) {
            super(message);
        }
    }
}
