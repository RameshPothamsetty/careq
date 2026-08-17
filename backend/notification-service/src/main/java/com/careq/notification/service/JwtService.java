package com.careq.notification.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Validates the JWT presented on the STOMP CONNECT frame of the real-time
 * notification socket. The gateway cannot enforce auth on the WebSocket
 * handshake (browsers cannot set HTTP headers there), so this service — the
 * WebSocket endpoint owner — parses the token itself with the shared
 * {@code app.jwt.secret} and exposes the caller's identity.
 *
 * <p>Fail-closed: an invalid, expired or otherwise unparseable token yields an
 * empty result and the connection is rejected (see WsAuthChannelInterceptor).
 */
@Service
public class JwtService {

    private final SecretKey signingKey;

    public JwtService(@Value("${app.jwt.secret}") String secretKey) {
        this.signingKey = Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses and validates a Bearer token. Returns the subject (user id) and
     * role when the token is signed with the shared secret and not expired;
     * empty otherwise.
     */
    public Optional<WsPrincipal> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String userId = claims.getSubject();
            if (userId == null || userId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new WsPrincipal(userId, claims.get("role", String.class)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Identity extracted from a valid socket token. */
    public record WsPrincipal(String userId, String role) {
    }
}
