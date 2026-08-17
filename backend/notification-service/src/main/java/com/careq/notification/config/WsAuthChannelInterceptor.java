package com.careq.notification.config;

import com.careq.notification.service.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * Authenticates the STOMP CONNECT frame of the real-time notification socket.
 *
 * <p>Browsers cannot set HTTP headers on a WebSocket handshake, so the JWT
 * travels inside the STOMP {@code Authorization} header (app-level frame, not
 * subject to CORS/header restrictions). The gateway lets the raw handshake
 * through; this interceptor is the security boundary. On a missing/invalid
 * token the CONNECT is rejected with a {@link WsAuthException}, which surfaces
 * to the client as a connection failure (fail-closed).
 */
@Component
public class WsAuthChannelInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public WsAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            throw new WsAuthException("Missing or malformed Authorization header on STOMP CONNECT");
        }

        Optional<JwtService.WsPrincipal> principal = jwtService.parse(header.substring(BEARER_PREFIX.length()));
        if (principal.isEmpty()) {
            throw new WsAuthException("Invalid or expired token on STOMP CONNECT");
        }

        // Expose the identity so handlers/subscriptions can read the caller.
        accessor.setUser((Principal) () -> principal.get().userId());
        accessor.setSessionAttributes(java.util.Map.of("userId", principal.get().userId()));
        return message;
    }

    /** Thrown to reject a CONNECT with an invalid token. */
    public static class WsAuthException extends RuntimeException {
        public WsAuthException(String message) {
            super(message);
        }
    }
}
