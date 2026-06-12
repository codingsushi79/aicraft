package dev.aicraft.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public record PluginConfig(
        String endpoint,
        String apiKey,
        String model,
        String systemPrompt,
        int timeoutSeconds,
        int maxHistoryMessages,
        List<String> stopSequences,
        double temperature
) {
    public static PluginConfig from(FileConfiguration config) {
        double temperature = config.getDouble("temperature", -1);
        return new PluginConfig(
                config.getString("endpoint", "http://localhost:11434/v1/chat/completions"),
                config.getString("api-key", "ollama"),
                config.getString("model", "llama3.2"),
                config.getString("system-prompt", "You are a helpful assistant inside a Minecraft server."),
                config.getInt("timeout-seconds", 120),
                config.getInt("max-history-messages", 20),
                config.getStringList("stop-sequences"),
                temperature
        );
    }

    public boolean hasTemperature() {
        return temperature >= 0;
    }
}
