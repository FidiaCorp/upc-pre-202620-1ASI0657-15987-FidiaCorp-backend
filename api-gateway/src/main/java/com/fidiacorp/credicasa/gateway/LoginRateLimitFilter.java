package com.fidiacorp.credicasa.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting en memoria (ventana fija por IP) para POST /api/v1/auth/login y /register: mitiga fuerza bruta antes
 * de llegar a identity (que además bloquea el segundo factor con 429 / Retry-After: 900).
 */
@Component
public class LoginRateLimitFilter implements GlobalFilter, Ordered {
    private record Window(long startedAt, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final long windowMs;
    private final Clock clock;

    public LoginRateLimitFilter(@Value("${app.rate-limit.auth.requests-per-window:30}") int limit,
                                @Value("${app.rate-limit.auth.window-seconds:60}") long windowSeconds, Clock clock) {
        this.limit = limit;
        this.windowMs = windowSeconds * 1000;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (exchange.getRequest().getMethod() != HttpMethod.POST || !(path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/register"))) {
            return chain.filter(exchange);
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        String key = path + "|" + (remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress());
        long now = clock.millis();
        Window window = windows.compute(key, (k, w) -> w == null || now - w.startedAt() >= windowMs ? new Window(now, 1) : new Window(w.startedAt(), w.count() + 1));
        if (window.count() > limit) {
            long retry = Math.max(1, (window.startedAt() + windowMs - now + 999) / 1000);
            exchange.getResponse().getHeaders().set("Retry-After", Long.toString(retry));
            return GatewayProblems.write(exchange, HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Vuelva a intentarlo más tarde.");
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }
}
