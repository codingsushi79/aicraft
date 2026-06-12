package dev.aicraft.command;

import dev.aicraft.service.LinkService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class AiLinkCommand implements CommandExecutor {

    private final LinkService linkService;

    public AiLinkCommand(LinkService linkService) {
        this.linkService = linkService;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String[] args
    ) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /ailink.", NamedTextColor.RED));
            return true;
        }

        try {
            var code = linkService.issueCodeForPlayer(player.getName(), player.getUniqueId());
            if (code.isEmpty()) {
                player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                        "No pending web link. Enter your username on the Aicraft web UI first.",
                        NamedTextColor.RED
                )));
                return true;
            }

            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Your link code (only you can see this): ",
                    NamedTextColor.GRAY
            )).append(Component.text(code.get(), NamedTextColor.GOLD, TextDecoration.BOLD)));
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Enter this code on the web UI to finish linking.",
                    NamedTextColor.GRAY
            )));
        } catch (Exception e) {
            player.sendMessage(ChatMessages.PREFIX.append(Component.text(
                    "Failed to create link code: " + e.getMessage(),
                    NamedTextColor.RED
            )));
        }
        return true;
    }
}
