package dev.aicraft.command;

import dev.aicraft.AicraftPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class EndChatCommand implements CommandExecutor {

    private final AicraftPlugin plugin;

    public EndChatCommand(AicraftPlugin plugin) {
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
            sender.sendMessage(Component.text("Only players can use /endchat.", NamedTextColor.RED));
            return true;
        }

        if (plugin.chatService().getActiveChat(player.getUniqueId()).isEmpty()) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "No active chat. Start one with /newchat.",
                    NamedTextColor.RED
            )));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.chatService().endChat(player.getUniqueId()).whenComplete((ignored, error) ->
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            if (error != null) {
                                player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                        "Failed to end chat: " + unwrap(error).getMessage(),
                                        NamedTextColor.RED
                                )));
                                return;
                            }
                            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                    "Chat ended.", NamedTextColor.GRAY)));
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
