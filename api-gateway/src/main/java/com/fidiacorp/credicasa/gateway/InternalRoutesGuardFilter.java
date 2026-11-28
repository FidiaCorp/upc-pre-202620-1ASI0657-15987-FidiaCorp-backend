package com.fidiacorp.credicasa.gateway;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Locale;

/**
 * La API servicio-a-servicio (/api/v1/internal/**) nunca se expone hacia Internet: 404 RFC 7807. Es un WebFilter (y no
 * un GlobalFilter) para actuar aunque ninguna ruta coincida, y rechaza también intentos de path traversal.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalRoutesGuardFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value().toLowerCase(Locale.ROOT);
        if (path.startsWith("/api/v1/internal") || path.contains("/../") || path.contains("%2e%2e") || path.contains("..;")) {
            return GatewayProblems.write(exchange, HttpStatus.NOT_FOUND, "El recurso solicitado no existe.");
        }
        return chain.filter(exchange);
    }
}
