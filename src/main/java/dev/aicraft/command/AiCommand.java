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

public final class AiCommand implements CommandExecutor {

    private final AicraftPlugin plugin;
    private final ChatService chatService;

    public AiCommand(AicraftPlugin plugin, ChatService chatService) {
        this.plugin = plugin;
        this.chatService = chatService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /ai.", NamedTextColor.RED));
            return true;
        }

        if (chatService.getActiveChat(player.getUniqueId()).isEmpty()) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "No active chat. Start one with /newchat or /reopenchat <id>.",
                    NamedTextColor.RED
            )));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Usage: /ai <message>",
                    NamedTextColor.RED
            )));
            return true;
        }

        String message = String.join(" ", args).trim();
        if (message.isEmpty()) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Usage: /ai <message>",
                    NamedTextColor.RED
            )));
            return true;
        }

        player.sendMessage(ChatMessages.PREFIX.append(Component.text("You: ", NamedTextColor.AQUA))
                .append(Component.text(message, NamedTextColor.WHITE)));
        player.sendMessage(ChatMessages.PREFIX.append(Component.text("Thinking...", NamedTextColor.DARK_GRAY)));

        chatService.sendMessage(player.getUniqueId(), message, null).whenComplete((reply, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline() || chatService.getActiveChat(player.getUniqueId()).isEmpty()) {
                        return;
                    }
                    if (error != null) {
                        player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                                "Error: " + unwrapError(error),
                                NamedTextColor.RED
                        )));
                        return;
                    }
                    sendWrappedReply(player, reply);
                })
        );

        return true;
    }

    private static String unwrapError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    private static void sendWrappedReply(Player player, String reply) {
        for (String line : reply.split("\n")) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text("AI: ", NamedTextColor.GREEN))
                    .append(Component.text(line, NamedTextColor.WHITE)));
        }
    }
}
