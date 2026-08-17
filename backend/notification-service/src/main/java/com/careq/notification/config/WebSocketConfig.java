package com.careq.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Real-time push for the in-app notification bell.
 *
 * <p>The frontend opens a WebSocket at {@code /ws} (proxied by the API gateway)
 * and subscribes to {@code /topic/notifications/{userId}}. When the RabbitMQ
 * consumer persists a notification it also broadcasts to that topic, so the
 * open tab updates instantly instead of waiting for the next polling tick.
 * Polling remains as the fallback for closed tabs and reconnect gaps.
 *
 * <p>Security: the endpoint is registered with permissive origins (the token
 * travels inside the STOMP CONNECT frame, validated by
 * {@link WsAuthChannelInterceptor} — not the HTTP handshake, so browser
 * origin checks here would be cosmetic). Origin restrictions still apply to
 * every authenticated HTTP route via CORS at the gateway.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WsAuthChannelInterceptor wsAuthChannelInterceptor;

    /** Comma-separated origins allowed to open the socket (from CORS_ALLOWED_ORIGINS). */
    private final String allowedOrigins;

    public WebSocketConfig(WsAuthChannelInterceptor wsAuthChannelInterceptor,
                           @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:3030,http://localhost:5173}") String allowedOrigins) {
        this.wsAuthChannelInterceptor = wsAuthChannelInterceptor;
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Server → client destinations: /topic/notifications/{userId}
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.split(","));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(wsAuthChannelInterceptor);
    }
}
