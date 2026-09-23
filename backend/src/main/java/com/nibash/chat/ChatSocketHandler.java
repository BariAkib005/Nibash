package com.nibash.chat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code /ws/chat/{roomId}/} — the live layer over the REST chat API (spec §12).
 *
 * <p>Inbound frames:
 * <ul>
 *   <li>{@code {"type":"message","text":"…"}} → broadcast {@code {"type":"message","text","sender"}} to
 *       the whole room, sender included (the spec's echo contract). {@code sender} is always the
 *       authenticated user's name, never the client's claim.</li>
 *   <li>{@code {"type":"typing"}} → {@code {"type":"typing","sender","user_id"}} to everyone else.</li>
 * </ul>
 * Messages that should <i>persist</i> are posted through {@code POST /api/chat/messages/}, which
 * then pushes a {@code message.created} frame here — the socket itself stores nothing.
 */
@Component
public class ChatSocketHandler extends TextWebSocketHandler {

    private static final int MAX_TEXT = 4000;

    private final ChatSocketRegistry registry;
    private final ObjectMapper json;

    public ChatSocketHandler(ChatSocketRegistry registry, ObjectMapper json) {
        this.registry = registry;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        registry.join(roomId(session), session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage frame) {
        JsonNode node;
        try {
            node = json.readTree(frame.getPayload());
        } catch (JacksonException malformed) {
            return; // not JSON — ignore rather than drop the connection
        }
        String type = node.path("type").asString("");
        String sender = (String) session.getAttributes().get(ChatHandshakeInterceptor.USER_NAME);

        switch (type) {
            case "message" -> {
                String text = node.path("text").asString("").trim();
                if (text.isEmpty() || text.length() > MAX_TEXT) {
                    return;
                }
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("type", "message");
                out.put("text", text);
                out.put("sender", sender);
                registry.broadcast(roomId(session), out, null);
            }
            case "typing" -> {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("type", "typing");
                out.put("sender", sender);
                out.put("user_id", session.getAttributes().get(ChatHandshakeInterceptor.USER_ID));
                registry.broadcast(roomId(session), out, session.getId());
            }
            default -> {
                // unknown frame types are ignored so newer clients never break older servers
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.leave(roomId(session), session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        registry.leave(roomId(session), session);
    }

    private static Long roomId(WebSocketSession session) {
        return (Long) session.getAttributes().get(ChatHandshakeInterceptor.ROOM_ID);
    }
}
