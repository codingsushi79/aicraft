package dev.aicraft.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PluginConfig(
        String endpoint,
        String apiKey,
        String model,
        String systemPrompt,
        int timeoutSeconds,
        int maxHistoryMessages
) {
    public static PluginConfig from(FileConfiguration config) {
        return new PluginConfig(
                config.getString("endpoint", "http://localhost:11434/v1/chat/completions"),
                config.getString("api-key", "ollama"),
                config.getString("model", "llama3.2"),
                config.getString("system-prompt", "You are a helpful assistant inside a Minecraft server."),
                config.getInt("timeout-seconds", 120),
                config.getInt("max-history-messages", 20)
        );
    }
}
