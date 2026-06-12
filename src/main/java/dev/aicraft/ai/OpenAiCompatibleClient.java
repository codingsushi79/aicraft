package dev.aicraft.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aicraft.config.PluginConfig;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public final class OpenAiCompatibleClient {

    private static final Gson GSON = new Gson();

    private final PluginConfig config;
    private final Logger logger;
    private final HttpClient httpClient;
    private final ExecutorService executor;

    public OpenAiCompatibleClient(PluginConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
                .build();
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "aicraft-http");
            thread.setDaemon(true);
            return thread;
        });
    }

    public CompletableFuture<String> chatAsync(List<ChatMessage> messages) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return chat(messages);
            } catch (AiException e) {
                throw new RuntimeException(e);
            }
        }, executor);
    }

    public String chat(List<ChatMessage> messages) throws AiException {
        JsonObject body = new JsonObject();
        body.addProperty("model", config.model());
        body.addProperty("stream", false);

        JsonArray messageArray = new JsonArray();
        for (ChatMessage message : messages) {
            JsonObject entry = new JsonObject();
            entry.addProperty("role", message.role());
            entry.addProperty("content", message.content());
            messageArray.add(entry);
        }
        body.add("messages", messageArray);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(config.endpoint()))
                .timeout(Duration.ofSeconds(config.timeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AiException("API returned HTTP " + response.statusCode() + ": " + truncate(response.body()));
            }
            return parseAssistantReply(response.body());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new AiException("Failed to reach AI endpoint: " + e.getMessage(), e);
        }
    }

    private String parseAssistantReply(String responseBody) throws AiException {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiException("API response missing choices: " + truncate(responseBody));
            }
            JsonObject message = choices.get(0).getAsJsonObject().getAsJsonObject("message");
            if (message == null || !message.has("content")) {
                throw new AiException("API response missing message content: " + truncate(responseBody));
            }
            String content = message.get("content").getAsString();
            if (content.isBlank()) {
                throw new AiException("AI returned an empty reply.");
            }
            return content.trim();
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException("Failed to parse API response: " + e.getMessage(), e);
        }
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    public static final class AiException extends Exception {
        public AiException(String message) {
            super(message);
        }

        public AiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
