package dev.aicraft.service;

import dev.aicraft.config.WebConfig;
import dev.aicraft.db.ChatRepository;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedPermissionData;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class RateLimitService {

    private final WebConfig webConfig;
    private final ChatRepository chatRepository;

    public RateLimitService(WebConfig webConfig, ChatRepository chatRepository) {
        this.webConfig = webConfig;
        this.chatRepository = chatRepository;
    }

    public void ensureCanCreateChat(UUID playerUuid, String username) {
        int limit = resolveDailyLimit(playerUuid, username);
        if (limit == Integer.MAX_VALUE) {
            return;
        }
        long since = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24);
        int used;
        try {
            used = chatRepository.countChatsCreatedSince(playerUuid, since);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        if (used >= limit) {
            throw new RateLimitException("Daily chat limit reached (" + limit + " per 24h). Try again later.");
        }
    }

    public int resolveDailyLimit(UUID playerUuid, String username) {
        Player online = Bukkit.getPlayer(playerUuid);
        if (online != null) {
            return resolveFromPlayer(online);
        }
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getUserManager().loadUser(playerUuid).join();
                if (user != null) {
                    CachedPermissionData permissions = user.getCachedData()
                            .getPermissionData(QueryOptions.defaultContextualOptions());
                    return resolveFromLuckPerms(permissions);
                }
            } catch (Exception ignored) {
                // Fall back to default limit.
            }
        }
        return webConfig.defaultChatsPerDay();
    }

    private int resolveFromPlayer(Player player) {
        int highest = -1;
        for (PermissionAttachmentInfo attachment : player.getEffectivePermissions()) {
            highest = Math.max(highest, parseChatLimit(attachment.getPermission()));
        }
        return highest >= 0 ? highest : webConfig.defaultChatsPerDay();
    }

    private int resolveFromLuckPerms(CachedPermissionData permissions) {
        if (permissions.checkPermission("aicraft.chats.unlimited").asBoolean()) {
            return Integer.MAX_VALUE;
        }
        int highest = -1;
        for (int value = 1000; value >= 1; value--) {
            if (permissions.checkPermission("aicraft.chats." + value).asBoolean()) {
                highest = value;
                break;
            }
        }
        return highest >= 0 ? highest : webConfig.defaultChatsPerDay();
    }

    private int parseChatLimit(String permission) {
        if ("aicraft.chats.unlimited".equals(permission)) {
            return Integer.MAX_VALUE;
        }
        if (permission.startsWith("aicraft.chats.")) {
            try {
                return Integer.parseInt(permission.substring("aicraft.chats.".length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    public static final class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }
}
