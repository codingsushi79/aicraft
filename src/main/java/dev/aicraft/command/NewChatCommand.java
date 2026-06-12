package dev.aicraft.command;

import dev.aicraft.AicraftPlugin;
import dev.aicraft.model.ChatSource;
import dev.aicraft.service.RateLimitService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class NewChatCommand implements CommandExecutor {

    private final AicraftPlugin plugin;

    public NewChatCommand(AicraftPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /newchat.", NamedTextColor.RED));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.chatService().startChat(player.getUniqueId(), player.getName(), ChatSource.INGAME)
                        .whenComplete((chat, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            if (error != null) {
                                Throwable cause = unwrap(error);
                                if (cause instanceof RateLimitService.RateLimitException rateLimit) {
                                    player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                            rateLimit.getMessage(), NamedTextColor.RED)));
                                } else {
                                    player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                            "Failed to start chat: " + cause.getMessage(), NamedTextColor.RED)));
                                }
                                return;
                            }
                            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                    "Chat #" + chat.playerChatNumber() + " started. Only you see this conversation.",
                                    NamedTextColor.GRAY
                            )));
                            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                    "Talk with /ai <message>. Reopen with /reopenchat "
                                            + chat.playerChatNumber() + ". End with /endchat.",
                                    NamedTextColor.GRAY
                            )));
                        }))
        );
        return true;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}
