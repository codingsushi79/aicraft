package dev.aicraft.command;

import dev.aicraft.AicraftPlugin;
import dev.aicraft.service.ChatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class ReopenChatCommand implements CommandExecutor {

    private final AicraftPlugin plugin;

    public ReopenChatCommand(AicraftPlugin plugin) {
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
            sender.sendMessage(Component.text("Only players can use /reopenchat.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Usage: /reopenchat <your chat id>",
                    NamedTextColor.RED
            )));
            return true;
        }

        int chatNumber;
        try {
            chatNumber = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Chat id must be a number, e.g. /reopenchat 1",
                    NamedTextColor.RED
            )));
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                plugin.chatService().reopenChat(player.getUniqueId(), chatNumber)
                        .whenComplete((chat, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (!player.isOnline()) {
                                return;
                            }
                            if (error != null) {
                                Throwable cause = unwrap(error);
                                if (cause instanceof ChatService.ChatException chatError) {
                                    player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                            chatError.getMessage(), NamedTextColor.RED)));
                                } else {
                                    player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                            "Failed to reopen chat: " + cause.getMessage(), NamedTextColor.RED)));
                                }
                                return;
                            }
                            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                    "Reopened chat #" + chat.playerChatNumber() + ". Continue with /ai <message>.",
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
