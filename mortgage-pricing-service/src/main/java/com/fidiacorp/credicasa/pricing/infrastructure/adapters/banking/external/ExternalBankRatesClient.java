package com.fidiacorp.credicasa.pricing.infrastructure.adapters.banking.external;

import com.fidiacorp.credicasa.pricing.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.shared.error.UpstreamServiceException;
import com.fidiacorp.credicasa.shared.money.Currency;
import com.fidiacorp.credicasa.shared.web.OutboundHeadersInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Cliente HTTP del proveedor externo de tasas ({@code external-bank-mock}, WireMock :8090). {@code readTimeout} de
 * 1000 ms; cualquier error/timeout/5xx se traduce a {@link UpstreamServiceException} para que el circuito
 * {@code bankingProvider} lo contabilice. Con {@code app.banking.external.enabled=false} el proveedor se considera
 * ausente (tests unitarios) y el circuito degrada inmediatamente a la caché SBS.
 */
@Component
public class ExternalBankRatesClient {
    public record ExternalRate(String bankCode, String bankName, BigDecimal minTea, BigDecimal maxTea, BigDecimal averageTea,
                               String productName, Integer maxTermMonths, BigDecimal maxLtv) {}

    public record ExternalRatesResponse(String provider, LocalDate asOfDate, String currency, List<ExternalRate> rates) {}

    public record LiveRates(List<BankRateBenchmark> rates, LocalDate asOfDate) {}

    private final RestClient client;
    private final boolean enabled;

    public ExternalBankRatesClient(@Value("${app.banking.external.base-url:http://localhost:8090}") String baseUrl,
                                   @Value("${app.banking.external.enabled:true}") boolean enabled,
                                   @Value("${app.banking.external.connect-timeout-ms:500}") int connectTimeoutMs,
                                   @Value("${app.banking.external.read-timeout-ms:1000}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .requestInterceptor(new OutboundHeadersInterceptor()).build();
        this.enabled = enabled;
    }

    public LiveRates fetch(Currency currency) {
        if (!enabled) {
            throw new UpstreamServiceException("Proveedor externo de tasas deshabilitado por configuración.", null);
        }
        try {
            ExternalRatesResponse body = client.get().uri(uri -> uri.path("/bank-rates/benchmarks").queryParam("currency", currency.name()).build())
                    .retrieve().body(ExternalRatesResponse.class);
            if (body == null || body.rates() == null || body.rates().isEmpty()) {
                throw new UpstreamServiceException("El proveedor externo devolvió una respuesta vacía.", null);
            }
            LocalDate asOf = body.asOfDate() == null ? LocalDate.now() : body.asOfDate();
            List<BankRateBenchmark> rates = body.rates().stream().map(r -> new BankRateBenchmark(r.bankCode(), r.bankName(), r.minTea(),
                    r.maxTea(), r.averageTea(), currency, r.productName(), r.maxTermMonths() == null ? 300 : r.maxTermMonths(),
                    r.maxLtv() == null ? new BigDecimal("0.90") : r.maxLtv(), asOf.atStartOfDay())).toList();
            return new LiveRates(rates, asOf);
        } catch (RestClientException e) {
            throw new UpstreamServiceException("Proveedor externo de tasas no disponible: " + e.getClass().getSimpleName(), e);
        }
    }
}
