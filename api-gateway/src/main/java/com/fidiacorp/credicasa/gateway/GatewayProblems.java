package com.fidiacorp.credicasa.gateway;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/** Respuestas RFC 7807 emitidas por el propio gateway (rutas internas, token revocado, rate limit). */
final class GatewayProblems {
    private GatewayProblems() {
    }

    static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String detail) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        Object correlation = exchange.getAttribute(CorrelationIdGlobalFilter.ATTRIBUTE);
        String slug = status.getReasonPhrase().toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
        String body = "{\"type\":\"https://api.credicasa.pe/problems/" + slug + "\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"detail\":\"" + detail + "\",\"instance\":\"" + exchange.getRequest().getPath().value()
                + "\",\"error\":\"" + status.getReasonPhrase() + "\",\"message\":\"" + detail + "\",\"timestamp\":\"" + LocalDateTime.now()
                + "\"" + (correlation == null ? "" : ",\"correlationId\":\"" + correlation + "\"") + "}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
