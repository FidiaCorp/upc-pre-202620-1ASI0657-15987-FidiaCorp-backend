package com.fidiacorp.credicasa.gateway;

import com.fidiacorp.credicasa.shared.web.CorrelationId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Normaliza (o genera) X-Correlation-Id, lo reenvía al microservicio y lo devuelve al cliente. */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {
    public static final String ATTRIBUTE = CorrelationIdGlobalFilter.class.getName() + ".id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String id = CorrelationId.sanitizeOrGenerate(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));
        ServerHttpRequest request = exchange.getRequest().mutate().headers(h -> h.set(CorrelationId.HEADER, id)).build();
        exchange.getAttributes().put(ATTRIBUTE, id);
        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, id);
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
