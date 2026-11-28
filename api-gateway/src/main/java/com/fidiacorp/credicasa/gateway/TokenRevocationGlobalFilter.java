package com.fidiacorp.credicasa.gateway;


import com.fidiacorp.credicasa.shared.security.JwtTokenValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * QA-03 (US04): revocación centralizada. Para cada Bearer con firma válida consulta a identity-subscription-service
 * (/api/v1/internal/tokens/status, caché corta por SHA-256 del token) y rechaza con 401 los tokens revocados o de
 * cuentas inexistentes. Si identity no responde se aplica fail-open (los MS siguen validando firma/expiración) salvo
 * que el token ya conste como revocado en caché.
 */
@Component
public class TokenRevocationGlobalFilter implements GlobalFilter, Ordered {
    private static final Logger log = LoggerFactory.getLogger(TokenRevocationGlobalFilter.class);
    private record Status(boolean active, long checkedAt) {}

    private final JwtTokenValidator validator;
    private final WebClient identity;
    private final String internalToken;
    private final long cacheMs;
    private final Clock clock;
    private final Map<String, Status> cache = new ConcurrentHashMap<>();

    public TokenRevocationGlobalFilter(@Value("${app.security.jwt.secret}") String secret, @Value("${app.routes.identity}") String identityUrl,
                                       @Value("${app.internal.token:}") String internalToken,
                                       @Value("${app.security.revocation-cache-ms:2000}") long cacheMs, WebClient.Builder builder, Clock clock) {
        this.validator = new JwtTokenValidator(secret);
        this.identity = builder.baseUrl(identityUrl).build();
        this.internalToken = internalToken;
        this.cacheMs = cacheMs;
        this.clock = clock;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        String token = authorization != null && authorization.startsWith("Bearer ") && authorization.length() > 7 ? authorization.substring(7).trim() : null;
        String path = exchange.getRequest().getPath().value();
        if (token == null || !path.startsWith("/api/v1/") || validator.validate(token).isEmpty()) {
            return chain.filter(exchange);
        }
        String key = sha256(token);
        Status cached = cache.get(key);
        long now = clock.millis();
        Mono<Boolean> active = cached != null && (now - cached.checkedAt() < cacheMs || !cached.active())
                ? Mono.just(cached.active())
                : identity.get().uri("/api/v1/internal/tokens/status").header(HttpHeaders.AUTHORIZATION, authorization)
                .headers(h -> { if (!internalToken.isBlank()) h.set("X-Internal-Token", internalToken); })
                .retrieve().bodyToMono(Map.class).timeout(Duration.ofSeconds(2))
                .map(body -> Boolean.TRUE.equals(body.get("active")))
                .doOnNext(result -> cache.put(key, new Status(result, clock.millis())))
                .onErrorResume(e -> {
                    log.warn("[SECURITY-AUDIT] Introspección de token no disponible ({}); fail-open con validación local", e.toString());
                    return Mono.just(true);
                });
        return active.flatMap(ok -> ok ? chain.filter(exchange)
                : GatewayProblems.write(exchange, HttpStatus.UNAUTHORIZED, "La sesión fue cerrada o el token ya no es válido."));
    }

    static String sha256(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
