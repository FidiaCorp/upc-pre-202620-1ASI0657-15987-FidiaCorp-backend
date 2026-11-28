package com.fidiacorp.credicasa.quotation.infrastructure.clients;

import com.fidiacorp.credicasa.shared.web.OutboundHeadersInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** RestClient por microservicio: propaga Authorization (JWT del usuario), X-Correlation-Id y X-Internal-Token. */
@Configuration
public class InternalClientsConfiguration {
    private static RestClient client(String baseUrl, String internalToken, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000);
        factory.setReadTimeout(readTimeoutMs);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor(new OutboundHeadersInterceptor(internalToken)).build();
    }

    @Bean
    RestClient identityClient(@Value("${app.services.identity-url}") String url, @Value("${app.internal.token:}") String token) {
        return client(url, token, 3000);
    }

    @Bean
    RestClient catalogClient(@Value("${app.services.catalog-url}") String url, @Value("${app.internal.token:}") String token) {
        return client(url, token, 3000);
    }

    @Bean
    RestClient pricingClient(@Value("${app.services.pricing-url}") String url, @Value("${app.internal.token:}") String token) {
        return client(url, token, 10000);
    }
}
