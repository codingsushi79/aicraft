package dev.aicraft.model;

import java.util.UUID;

public record WebSession(String token, UUID playerUuid, String username, long expiresAt) {
    public boolean isExpired() {
        return System.currentTimeMillis() > expiresAt;
    }
}
