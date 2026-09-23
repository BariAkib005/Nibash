package com.nibash.chat;

import com.nibash.auth.AuthToken;
import com.nibash.auth.AuthTokenRepository;
import com.nibash.common.ApiException;
import com.nibash.user.User;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the chat handshake — the spec §15.9 hardening of the originally open socket.
 *
 * <p>Browsers cannot set an {@code Authorization} header on a WebSocket, so the token arrives as
 * {@code ?token=}. The room is checked with the same {@link ChatAccess} rule the REST API uses, so
 * a socket can never listen to a room its user could not read. The check runs here, during the
 * HTTP upgrade request, because tenancy is request-scoped and no request exists once the socket
 * is open; the result is carried on the session's attributes.
 */
@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    static final String ROOM_ID = "chat.roomId";
    static final String USER_ID = "chat.userId";
    static final String USER_NAME = "chat.userName";

    private final AuthTokenRepository tokens;
    private final ChatAccess access;

    public ChatHandshakeInterceptor(AuthTokenRepository tokens, ChatAccess access) {
        this.tokens = tokens;
        this.access = access;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attributes) {
        Long roomId = roomIdFrom(request.getURI().getPath());
        String token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("token");

        Optional<User> user = token == null || token.isBlank()
                ? Optional.empty()
                : tokens.findByKey(token.trim()).map(AuthToken::getUser).filter(User::isActive);
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        if (roomId == null) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }
        try {
            access.requireReadable(user.get(), roomId);
        } catch (ApiException denied) {
            response.setStatusCode(HttpStatus.NOT_FOUND);
            return false;
        }

        attributes.put(ROOM_ID, roomId);
        attributes.put(USER_ID, user.get().getId());
        attributes.put(USER_NAME, user.get().getName());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler handler, Exception exception) {
        // nothing to clean up
    }

    /** {@code /ws/chat/12/} or {@code /ws/chat/12} → 12. */
    static Long roomIdFrom(String path) {
        String[] parts = path.replaceAll("/+$", "").split("/");
        try {
            return Long.valueOf(parts[parts.length - 1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }
}
