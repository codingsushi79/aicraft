package dev.aicraft.command;

import dev.aicraft.model.ChatRecord;
import dev.aicraft.model.ChatSource;
import dev.aicraft.service.ChatService;
import dev.aicraft.service.RateLimitService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class NewChatCommand implements CommandExecutor {

    private final ChatService chatService;

    public NewChatCommand(ChatService chatService) {
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
            sender.sendMessage(Component.text("Only players can use /newchat.", NamedTextColor.RED));
            return true;
        }

        try {
            ChatRecord chat = chatService.startChat(player.getUniqueId(), player.getName(), ChatSource.INGAME);
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Chat #" + chat.playerChatNumber() + " started. Only you see this conversation.",
                    NamedTextColor.GRAY
            )));
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Talk with /ai <message>. Reopen with /reopenchat " + chat.playerChatNumber() + ". End with /endchat.",
                    NamedTextColor.GRAY
            )));
        } catch (RateLimitService.RateLimitException e) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(e.getMessage(), NamedTextColor.RED)));
        } catch (Exception e) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Failed to start chat: " + e.getMessage(),
                    NamedTextColor.RED
            )));
        }
        return true;
    }
}
