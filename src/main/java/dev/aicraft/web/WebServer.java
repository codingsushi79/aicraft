package dev.aicraft.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.aicraft.config.WebConfig;
import dev.aicraft.model.ChatRecord;
import dev.aicraft.model.ChatSource;
import dev.aicraft.model.StoredMessage;
import dev.aicraft.model.WebSession;
import dev.aicraft.service.ChatService;
import dev.aicraft.service.LinkService;
import dev.aicraft.service.RateLimitService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import io.javalin.http.staticfiles.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class WebServer implements AutoCloseable {

    private static final Gson GSON = new Gson();
    private static final String SESSION_COOKIE = "aicraft_session";

    private final WebConfig webConfig;
    private final LinkService linkService;
    private final ChatService chatService;
    private final Logger logger;
    private Javalin app;

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

    public void start() {
        if (app != null) {
            return;
        }
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/web";
                staticFiles.location = Location.CLASSPATH;
            });
        });

        app.post("/api/link/start", this::handleLinkStart);
        app.post("/api/link/verify", this::handleLinkVerify);
        app.post("/api/logout", this::handleLogout);
        app.get("/api/me", this::handleMe);
        app.get("/api/chats", this::handleListChats);
        app.post("/api/chats", this::handleCreateChat);
        app.get("/api/chats/{id}/messages", this::handleListMessages);
        app.post("/api/chats/{id}/messages", this::handleSendMessage);

        app.start(webConfig.bindAddress(), webConfig.port());
        logger.info("Aicraft web UI running on http://" + webConfig.bindAddress() + ":" + webConfig.port());
    }

    @Override
    public void close() {
        if (app != null) {
            app.stop();
            app = null;
            logger.info("Aicraft web UI stopped.");
        }
    }

    private void handleLinkStart(Context ctx) {
        JsonObject body = parseBody(ctx);
        String username = text(body, "username");
        if (username == null || username.isBlank()) {
            error(ctx, HttpStatus.BAD_REQUEST, "Username is required.");
            return;
        }
        try {
            linkService.startLinkRequest(username.trim());
            json(ctx, Map.of(
                    "status", "waiting_for_game",
                    "message", "Run /ailink in-game, then enter the code shown to you."
            ));
        } catch (Exception e) {
            error(ctx, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void handleLinkVerify(Context ctx) {
        JsonObject body = parseBody(ctx);
        String username = text(body, "username");
        String code = text(body, "code");
        if (username == null || code == null || username.isBlank() || code.isBlank()) {
            error(ctx, HttpStatus.BAD_REQUEST, "Username and code are required.");
            return;
        }
        try {
            WebSession session = linkService.confirmLink(username.trim(), code.trim());
            ctx.cookie(SESSION_COOKIE, session.token(), 60 * 60 * 24 * webConfig.webSessionDays());
            json(ctx, Map.of(
                    "username", session.username(),
                    "playerUuid", session.playerUuid().toString()
            ));
        } catch (LinkService.LinkException e) {
            error(ctx, HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            error(ctx, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void handleLogout(Context ctx) {
        session(ctx).ifPresent(session -> {
            try {
                linkService.logout(session.token());
            } catch (Exception ignored) {
                // Best effort logout.
            }
        });
        ctx.removeCookie(SESSION_COOKIE);
        json(ctx, Map.of("status", "logged_out"));
    }

    private void handleMe(Context ctx) {
        Optional<WebSession> session = session(ctx);
        if (session.isEmpty()) {
            error(ctx, HttpStatus.UNAUTHORIZED, "Not linked.");
            return;
        }
        WebSession webSession = session.get();
        json(ctx, Map.of(
                "username", webSession.username(),
                "playerUuid", webSession.playerUuid().toString()
        ));
    }

    private void handleListChats(Context ctx) {
        Optional<WebSession> session = requireSession(ctx);
        if (session.isEmpty()) {
            return;
        }
        try {
            List<ChatRecord> chats = chatService.listChats(session.get().playerUuid());
            json(ctx, chats.stream().map(this::chatToMap).toList());
        } catch (Exception e) {
            error(ctx, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void handleCreateChat(Context ctx) {
        Optional<WebSession> session = requireSession(ctx);
        if (session.isEmpty()) {
            return;
        }
        WebSession webSession = session.get();
        try {
            ChatRecord chat = chatService.startChat(webSession.playerUuid(), webSession.username(), ChatSource.WEB);
            json(ctx, chatToMap(chat));
        } catch (RateLimitService.RateLimitException e) {
            error(ctx, HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        } catch (Exception e) {
            error(ctx, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void handleListMessages(Context ctx) {
        Optional<WebSession> session = requireSession(ctx);
        if (session.isEmpty()) {
            return;
        }
        long chatId = ctx.pathParamAsClass("id", Long.class).get();
        try {
            List<StoredMessage> messages = chatService.listMessages(session.get().playerUuid(), chatId);
            json(ctx, messages.stream()
                    .filter(message -> !"system".equals(message.role()))
                    .map(message -> Map.of(
                            "id", message.id(),
                            "role", message.role(),
                            "content", message.content(),
                            "createdAt", message.createdAt()
                    ))
                    .toList());
        } catch (ChatService.ChatException e) {
            error(ctx, HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            error(ctx, HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void handleSendMessage(Context ctx) {
        Optional<WebSession> session = requireSession(ctx);
        if (session.isEmpty()) {
            return;
        }
        JsonObject body = parseBody(ctx);
        String content = text(body, "content");
        if (content == null || content.isBlank()) {
            error(ctx, HttpStatus.BAD_REQUEST, "Message content is required.");
            return;
        }
        long chatId = ctx.pathParamAsClass("id", Long.class).get();
        try {
            chatService.reopenChatById(session.get().playerUuid(), chatId);
            String reply = chatService.sendMessage(session.get().playerUuid(), content.trim(), null).join();
            json(ctx, Map.of("reply", reply));
        } catch (ChatService.ChatException e) {
            error(ctx, HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            error(ctx, HttpStatus.INTERNAL_SERVER_ERROR, cause.getMessage());
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

    private Optional<WebSession> requireSession(Context ctx) {
        Optional<WebSession> session = session(ctx);
        if (session.isEmpty()) {
            error(ctx, HttpStatus.UNAUTHORIZED, "Not linked.");
        }
        return session;
    }

    private Optional<WebSession> session(Context ctx) {
        String token = ctx.cookie(SESSION_COOKIE);
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            return linkService.findSession(token);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static JsonObject parseBody(Context ctx) {
        if (ctx.body().isBlank()) {
            return new JsonObject();
        }
        return GSON.fromJson(ctx.body(), JsonObject.class);
    }

    private static String text(JsonObject body, String key) {
        return body.has(key) && !body.get(key).isJsonNull() ? body.get(key).getAsString() : null;
    }

    private static void json(Context ctx, Object body) {
        ctx.contentType("application/json");
        ctx.result(GSON.toJson(body));
    }

    private static void error(Context ctx, HttpStatus status, String message) {
        ctx.status(status);
        json(ctx, Map.of("error", message));
    }
}
