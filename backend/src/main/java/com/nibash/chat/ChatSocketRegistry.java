package com.nibash.chat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import tools.jackson.databind.ObjectMapper;

/**
 * The in-process room groups (spec §12): {@code roomId -> sessions}. Single-node and in-memory by
 * design; scaling out would put Redis pub/sub behind {@link #broadcast}.
 *
 * <p>Each session is wrapped in a {@link ConcurrentWebSocketSessionDecorator}: a REST request
 * thread and a socket thread can both broadcast into the same room at once, and a raw
 * {@link WebSocketSession} is not safe for concurrent sends.
 */
@Component
public class ChatSocketRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatSocketRegistry.class);
    private static final int SEND_TIME_LIMIT_MS = 5_000;
    private static final int BUFFER_LIMIT_BYTES = 256 * 1024;

    private final Map<Long, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    public ChatSocketRegistry(ObjectMapper json) {
        this.json = json;
    }

    public void join(Long roomId, WebSocketSession session) {
        rooms.computeIfAbsent(roomId, id -> new ConcurrentHashMap<>())
                .put(session.getId(), new ConcurrentWebSocketSessionDecorator(session, SEND_TIME_LIMIT_MS, BUFFER_LIMIT_BYTES));
    }

    public void leave(Long roomId, WebSocketSession session) {
        Map<String, WebSocketSession> group = rooms.get(roomId);
        if (group != null) {
            group.remove(session.getId());
            if (group.isEmpty()) {
                rooms.remove(roomId, group);
            }
        }
    }

    public int size(Long roomId) {
        Map<String, WebSocketSession> group = rooms.get(roomId);
        return group == null ? 0 : group.size();
    }

    /** A message persisted through REST — delivered to everyone in the room, sender included. */
    public void broadcastCreated(Long roomId, ChatMessageController.MessageDto message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "message.created");
        payload.put("message", message);
        broadcast(roomId, payload, null);
    }

    /** Sends {@code payload} to every open socket in the room except {@code excludeSessionId}. */
    public void broadcast(Long roomId, Map<String, Object> payload, String excludeSessionId) {
        Map<String, WebSocketSession> group = rooms.get(roomId);
        if (group == null || group.isEmpty()) {
            return;
        }
        TextMessage frame = new TextMessage(json.writeValueAsString(payload));
        group.forEach((id, session) -> {
            if (id.equals(excludeSessionId)) {
                return;
            }
            if (!session.isOpen()) {
                group.remove(id);
                return;
            }
            try {
                session.sendMessage(frame);
            } catch (IOException | RuntimeException e) {
                // A dead or slow client must not stop delivery to everyone else.
                log.debug("Dropping chat socket {} in room {}: {}", id, roomId, e.getMessage());
                group.remove(id);
            }
        });
    }
}
