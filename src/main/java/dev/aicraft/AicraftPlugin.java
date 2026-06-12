package dev.aicraft;

import dev.aicraft.ai.ChatSessionManager;
import dev.aicraft.ai.OpenAiCompatibleClient;
import dev.aicraft.command.AiCommand;
import dev.aicraft.command.AiLinkCommand;
import dev.aicraft.command.EndChatCommand;
import dev.aicraft.command.NewChatCommand;
import dev.aicraft.command.ReopenChatCommand;
import dev.aicraft.config.PluginConfig;
import dev.aicraft.config.WebConfig;
import dev.aicraft.db.ChatRepository;
import dev.aicraft.db.DatabaseManager;
import dev.aicraft.db.LinkRepository;
import dev.aicraft.service.ChatService;
import dev.aicraft.service.LinkService;
import dev.aicraft.service.RateLimitService;
import dev.aicraft.web.WebServer;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class AicraftPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private WebConfig webConfig;
    private OpenAiCompatibleClient aiClient;
    private DatabaseManager databaseManager;
    private ChatService chatService;
    private LinkService linkService;
    private WebServer webServer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("web.yml", false);

        try {
            reloadLocalConfig();
        } catch (Exception e) {
            getLogger().severe("Failed to initialize Aicraft: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!registerCommand("newchat", new NewChatCommand(chatService))
                || !registerCommand("endchat", new EndChatCommand(chatService))
                || !registerCommand("ai", new AiCommand(this, chatService))
                || !registerCommand("reopenchat", new ReopenChatCommand(chatService))
                || !registerCommand("ailink", new AiLinkCommand(linkService))) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (webConfig.enabled()) {
            webServer = new WebServer(webConfig, linkService, chatService, getLogger());
            webServer.start();
        }

        getLogger().info("Aicraft enabled — endpoint: " + pluginConfig.endpoint());
    }

    @Override
    public void onDisable() {
        if (webServer != null) {
            webServer.close();
            webServer = null;
        }
        if (chatService != null) {
            chatService.sessionManager().clearAll();
        }
        if (aiClient != null) {
            aiClient.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
            databaseManager = null;
        }
    }

    public void reloadLocalConfig() {
        reloadConfig();
        pluginConfig = PluginConfig.from(getConfig());
        webConfig = WebConfig.from(loadWebConfig());

        if (aiClient != null) {
            aiClient.shutdown();
        }
        aiClient = new OpenAiCompatibleClient(pluginConfig, getLogger());

        if (databaseManager != null) {
            databaseManager.close();
        }
        databaseManager = new DatabaseManager(webConfig.database(), getDataFolder(), getLogger());

        ChatRepository chatRepository = new ChatRepository(databaseManager);
        LinkRepository linkRepository = new LinkRepository(databaseManager);
        ChatSessionManager sessionManager = new ChatSessionManager(pluginConfig);
        RateLimitService rateLimitService = new RateLimitService(webConfig, chatRepository);
        linkService = new LinkService(webConfig, linkRepository);
        chatService = new ChatService(pluginConfig, chatRepository, sessionManager, rateLimitService, aiClient);
    }

    private FileConfiguration loadWebConfig() {
        File webFile = new File(getDataFolder(), "web.yml");
        return YamlConfiguration.loadConfiguration(webFile);
    }

    public PluginConfig pluginConfig() {
        return pluginConfig;
    }

    public WebConfig webConfig() {
        return webConfig;
    }

    public ChatService chatService() {
        return chatService;
    }

    private boolean registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' missing from plugin.yml — disabling.");
            return false;
        }
        command.setExecutor(executor);
        return true;
    }
}
