package com.careq.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private final String secretKey;

    /**
     * Assumption: The gateway validates JWT tokens using the same secret key
     * as auth-service. Protected routes require a valid Bearer token.
     * Public routes (/api/auth/**) are skipped from validation.
     * When a token is invalid/missing on a protected route, a 401 is returned
     * before the request reaches the downstream service.
     *
     * TODO: For fine-grained role checks, individual services still need
     *       their own JWT validation. This filter only ensures a valid token
     *       is present — it does NOT enforce role-level permissions at the gateway.
     */
    public JwtAuthGlobalFilter(@Value("${app.jwt.secret}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // Skip public auth routes and profile pictures (images loaded via <img> tags)
        if (path.startsWith("/api/auth/signup")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/health")
                || path.startsWith("/api/users/profile-pictures/")) {
            return chain.filter(exchange);
        }

        // Public health probes for every service (Day 9 review fix): the API
        // contract documents all four /health endpoints as unauthenticated.
        if (path.equals("/api/users/health")
                || path.equals("/api/doctors/health")
                || path.equals("/api/queue/health")
                || path.equals("/api/notifications/health")) {
            return chain.filter(exchange);
        }

        // Skip Eureka routes (internal)
        if (path.startsWith("/api/eureka") || path.startsWith("/eureka")) {
            return chain.filter(exchange);
        }

        // Skip Swagger / OpenAPI routes (Day 9) — the centralized docs UI is
        // public in this dev/demo setup so it can be browsed and tested without
        // a token. Swagger UI + aggregated specs + static assets are all covered.
        if (path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars")
                || path.equals("/favicon.ico")) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        String token = authHeader.substring(7);

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // Extract identity claims from the JWT and forward as headers so
        // downstream services never need to re-parse the token. fullName/email
        // are used by user-service to store display names (admin user list,
        // queue patient names) — they may be absent on tokens issued before
        // Day 7a, so the gateway simply omits them in that case.
        String userId = claims.getSubject();
        String role = claims.get("role", String.class);
        String fullName = claims.get("fullName", String.class);
        String email = claims.get("email", String.class);

        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-User-Role", role);
        if (fullName != null && !fullName.isBlank()) {
            requestBuilder.header("X-User-Name", fullName);
        }
        if (email != null && !email.isBlank()) {
            requestBuilder.header("X-User-Email", email);
        }
        ServerHttpRequest mutatedRequest = requestBuilder.build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return -1; // High priority — run before other filters
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes(StandardCharsets.UTF_8));
    }

}
