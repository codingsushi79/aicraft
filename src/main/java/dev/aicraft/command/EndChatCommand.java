package dev.aicraft.command;

import dev.aicraft.service.ChatService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class EndChatCommand implements CommandExecutor {

    private final ChatService chatService;

    public EndChatCommand(ChatService chatService) {
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
            sender.sendMessage(Component.text("Only players can use /endchat.", NamedTextColor.RED));
            return true;
        }

        if (chatService.getActiveChat(player.getUniqueId()).isEmpty()) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "No active chat. Start one with /newchat.",
                    NamedTextColor.RED
            )));
            return true;
        }

        try {
            chatService.endChat(player.getUniqueId());
            player.sendMessage(ChatMessages.PREFIX.append(Component.text("Chat ended.", NamedTextColor.GRAY)));
        } catch (Exception e) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Failed to end chat: " + e.getMessage(),
                    NamedTextColor.RED
            )));
        }
        return true;
    }
}
