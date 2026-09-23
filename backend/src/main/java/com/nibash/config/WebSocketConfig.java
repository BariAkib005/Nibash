package com.nibash.config;

import com.nibash.chat.ChatHandshakeInterceptor;
import com.nibash.chat.ChatSocketHandler;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Mounts the chat socket at {@code /ws/chat/{roomId}/} (spec §12), with and without the trailing
 * slash. Same-origin handshakes (production, behind Nginx) are always allowed; the configured dev
 * origins cover the Vite server, whose proxy forwards the browser's {@code Origin} unchanged.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatSocketHandler chatHandler;
    private final ChatHandshakeInterceptor chatHandshake;

    @Value("${nibash.cors.allowed-origins}")
    private String allowedOrigins;

    public WebSocketConfig(ChatSocketHandler chatHandler, ChatHandshakeInterceptor chatHandshake) {
        this.chatHandler = chatHandler;
        this.chatHandshake = chatHandshake;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatHandler, "/ws/chat/*", "/ws/chat/*/")
                .addInterceptors(chatHandshake)
                .setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toArray(String[]::new));
    }
}
