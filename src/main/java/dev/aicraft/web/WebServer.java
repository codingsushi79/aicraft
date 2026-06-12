package dev.aicraft.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.aicraft.config.WebConfig;
import dev.aicraft.model.ChatRecord;
import dev.aicraft.model.ChatSource;
import dev.aicraft.model.StoredMessage;
import dev.aicraft.model.WebSession;
import dev.aicraft.service.ChatService;
import dev.aicraft.service.LinkService;
import dev.aicraft.service.RateLimitService;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WebServer implements AutoCloseable {

    private static final Gson GSON = new Gson();
    private static final String SESSION_COOKIE = "aicraft_session";
    private static final Pattern CHAT_MESSAGES_PATH =
            Pattern.compile("^/api/chats/(\\d+)/messages$");

    private final WebConfig webConfig;
    private final LinkService linkService;
    private final ChatService chatService;
    private final Logger logger;
    private HttpServer server;
    private ExecutorService executor;

    public WebServer(
            WebConfig webConfig,
            LinkService linkService,
            ChatService chatService,
            Logger logger
    ) {
        this.webConfig = webConfig;
        this.linkService = linkService;
        this.chatService = chatService;
        this.logger = logger;
    }

    public void start() throws IOException {
        if (server != null) {
            return;
        }
        InetSocketAddress address = new InetSocketAddress(webConfig.bindAddress(), webConfig.port());
        server = HttpServer.create(address, 0);
        executor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "aicraft-web");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
        logger.info("Aicraft web UI running on http://" + webConfig.bindAddress() + ":" + webConfig.port());
    }

    @Override
    public void close() {
        if (server != null) {
            server.stop(0);
            server = null;
            logger.info("Aicraft web UI stopped.");
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void handle(HttpExchange exchange) {
        try {
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                handleApi(exchange, path);
            } else {
                handleStatic(exchange, path);
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "Web request failed: " + exchange.getRequestURI(), e);
            try {
                error(exchange, 500, "Internal server error.");
            } catch (IOException ignored) {
                // Exchange may already be closed.
            }
        }
    }

    private void handleStatic(HttpExchange exchange, String path) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        String resourcePath = path.equals("/") ? "/index.html" : path;
        String classpath = "/web" + resourcePath;
        try (InputStream stream = WebServer.class.getResourceAsStream(classpath)) {
            if (stream == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            byte[] body = stream.readAllBytes();
            Headers headers = exchange.getResponseHeaders();
            headers.set("Content-Type", contentType(resourcePath));
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(body);
            }
        }
    }

    private void handleApi(HttpExchange exchange, String path) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase();

        if ("/api/link/start".equals(path) && "POST".equals(method)) {
            handleLinkStart(exchange);
            return;
        }
        if ("/api/link/verify".equals(path) && "POST".equals(method)) {
            handleLinkVerify(exchange);
            return;
        }
        if ("/api/logout".equals(path) && "POST".equals(method)) {
            handleLogout(exchange);
            return;
        }
        if ("/api/me".equals(path) && "GET".equals(method)) {
            handleMe(exchange);
            return;
        }
        if ("/api/chats".equals(path) && "GET".equals(method)) {
            handleListChats(exchange);
            return;
        }
        if ("/api/chats".equals(path) && "POST".equals(method)) {
            handleCreateChat(exchange);
            return;
        }

        Matcher matcher = CHAT_MESSAGES_PATH.matcher(path);
        if (matcher.matches()) {
            long chatId = Long.parseLong(matcher.group(1));
            if ("GET".equals(method)) {
                handleListMessages(exchange, chatId);
            } else if ("POST".equals(method)) {
                handleSendMessage(exchange, chatId);
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
            return;
        }

        exchange.sendResponseHeaders(404, -1);
    }

    private void handleLinkStart(HttpExchange exchange) throws IOException {
        JsonObject body = parseBody(exchange);
        String username = text(body, "username");
        if (username == null || username.isBlank()) {
            error(exchange, 400, "Username is required.");
            return;
        }
        try {
            linkService.startLinkRequest(username.trim());
            json(exchange, 200, Map.of(
                    "status", "waiting_for_game",
                    "message", "Run /ailink in-game, then enter the code shown to you."
            ));
        } catch (Exception e) {
            error(exchange, 500, e.getMessage() != null ? e.getMessage() : "Failed to start link.");
        }
    }

    private void handleLinkVerify(HttpExchange exchange) throws IOException {
        JsonObject body = parseBody(exchange);
        String username = text(body, "username");
        String code = text(body, "code");
        if (username == null || code == null || username.isBlank() || code.isBlank()) {
            error(exchange, 400, "Username and code are required.");
            return;
        }
        try {
            WebSession session = linkService.confirmLink(username.trim(), code.trim());
            Headers headers = exchange.getResponseHeaders();
            headers.add("Set-Cookie", SESSION_COOKIE + "=" + session.token()
                    + "; Path=/; HttpOnly; Max-Age=" + (60L * 60 * 24 * webConfig.webSessionDays()));
            json(exchange, 200, Map.of(
                    "username", session.username(),
                    "playerUuid", session.playerUuid().toString()
            ));
        } catch (LinkService.LinkException e) {
            error(exchange, 400, e.getMessage());
        } catch (Exception e) {
            error(exchange, 500, e.getMessage() != null ? e.getMessage() : "Failed to verify link.");
        }
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        session(exchange).ifPresent(session -> {
            try {
                linkService.logout(session.token());
            } catch (Exception ignored) {
                // Best effort logout.
            }
        });
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; HttpOnly; Max-Age=0");
        json(exchange, 200, Map.of("status", "logged_out"));
    }

    private void handleMe(HttpExchange exchange) throws IOException {
        Optional<WebSession> session = session(exchange);
        if (session.isEmpty()) {
            error(exchange, 401, "Not linked.");
            return;
        }
        WebSession webSession = session.get();
        json(exchange, 200, Map.of(
                "username", webSession.username(),
                "playerUuid", webSession.playerUuid().toString()
        ));
    }

    private void handleListChats(HttpExchange exchange) throws IOException {
        Optional<WebSession> session = requireSession(exchange);
        if (session.isEmpty()) {
            return;
        }
        List<ChatRecord> chats = chatService.listChats(session.get().playerUuid());
        json(exchange, 200, chats.stream().map(this::chatToMap).toList());
    }

    private void handleCreateChat(HttpExchange exchange) throws IOException {
        Optional<WebSession> session = requireSession(exchange);
        if (session.isEmpty()) {
            return;
        }
        WebSession webSession = session.get();
        try {
            ChatRecord chat = chatService.startChat(webSession.playerUuid(), webSession.username(), ChatSource.WEB);
            json(exchange, 200, chatToMap(chat));
        } catch (RateLimitService.RateLimitException e) {
            error(exchange, 429, e.getMessage());
        }
    }

    private void handleListMessages(HttpExchange exchange, long chatId) throws IOException {
        Optional<WebSession> session = requireSession(exchange);
        if (session.isEmpty()) {
            return;
        }
        try {
            List<StoredMessage> messages = chatService.listMessages(session.get().playerUuid(), chatId);
            json(exchange, 200, messages.stream()
                    .filter(message -> !"system".equals(message.role()))
                    .map(message -> Map.of(
                            "id", message.id(),
                            "role", message.role(),
                            "content", message.content(),
                            "createdAt", message.createdAt()
                    ))
                    .toList());
        } catch (ChatService.ChatException e) {
            error(exchange, 404, e.getMessage());
        }
    }

    private void handleSendMessage(HttpExchange exchange, long chatId) throws IOException {
        Optional<WebSession> session = requireSession(exchange);
        if (session.isEmpty()) {
            return;
        }
        JsonObject body = parseBody(exchange);
        String content = text(body, "content");
        if (content == null || content.isBlank()) {
            error(exchange, 400, "Message content is required.");
            return;
        }
        try {
            chatService.reopenChatById(session.get().playerUuid(), chatId);
            String reply = chatService.sendMessage(session.get().playerUuid(), content.trim(), null).join();
            json(exchange, 200, Map.of("reply", reply));
        } catch (ChatService.ChatException e) {
            error(exchange, 404, e.getMessage());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            error(exchange, 500, cause.getMessage() != null ? cause.getMessage() : "Request failed.");
        }
    }

    private Map<String, Object> chatToMap(ChatRecord chat) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", chat.id());
        map.put("chatNumber", chat.playerChatNumber());
        map.put("source", chat.source().name());
        map.put("createdAt", chat.createdAt());
        map.put("updatedAt", chat.updatedAt());
        map.put("ended", chat.endedAt() != null);
        return map;
    }

    private Optional<WebSession> requireSession(HttpExchange exchange) throws IOException {
        Optional<WebSession> session = session(exchange);
        if (session.isEmpty()) {
            error(exchange, 401, "Not linked.");
        }
        return session;
    }

    private Optional<WebSession> session(HttpExchange exchange) {
        String token = cookie(exchange, SESSION_COOKIE);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return linkService.findSession(token);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String cookie(HttpExchange exchange, String name) {
        List<String> cookieHeaders = exchange.getRequestHeaders().get("Cookie");
        if (cookieHeaders == null) {
            return null;
        }
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String trimmed = part.trim();
                int equals = trimmed.indexOf('=');
                if (equals > 0 && trimmed.substring(0, equals).equals(name)) {
                    return trimmed.substring(equals + 1);
                }
            }
        }
        return null;
    }

    private static JsonObject parseBody(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return new JsonObject();
        }
        return GSON.fromJson(raw, JsonObject.class);
    }

    private static String text(JsonObject body, String key) {
        return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : null;
    }

    private static void json(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void error(HttpExchange exchange, int status, String message) throws IOException {
        json(exchange, status, Map.of("error", message));
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        return "text/html; charset=utf-8";
    }
}
