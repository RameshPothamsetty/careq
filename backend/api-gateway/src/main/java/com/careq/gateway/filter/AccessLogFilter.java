package com.careq.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * access-log audit trail at the edge.
 *
 * <p>Every request that reaches the gateway is logged to the {@code ACCESS}
 * logger as a single-line record: method, path, status, duration, caller IP
 * and (when authenticated) the X-User-Id header that {@link
 * JwtAuthGlobalFilter} stamped from the JWT. These lines are the audit
 * store — retained in Azure container logs and queryable via Log Analytics
 * (docs/12_MONITORING.md).
 *
 * <p>Runs AFTER JwtAuthGlobalFilter (order -1), so authenticated exchanges
 * already carry X-User-Id; whitelisted public paths log {@code user=-}.
 */
@Component
public class AccessLogFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger("ACCESS");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long start = System.currentTimeMillis();
        String method = exchange.getRequest().getMethod() != null
                ? exchange.getRequest().getMethod().name() : "?";
        String path = exchange.getRequest().getURI().getPath();
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String ip = resolveClientIp(exchange);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
            long durationMs = System.currentTimeMillis() - start;
            log.info("method={} path={} status={} duration_ms={} user={} ip={}",
                    method, path, status, durationMs, userId == null ? "-" : userId, ip == null ? "-" : ip);
        }));
    }

    @Override
    public int getOrder() {
        return 0; // after JwtAuthGlobalFilter (-1) so X-User-Id is already set
    }

    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress() : null;
    }
}
