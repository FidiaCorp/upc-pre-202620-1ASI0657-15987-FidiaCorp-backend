package com.fidiacorp.credicasa.infrastructure.adapters.banking;

import com.fidiacorp.credicasa.domain.model.BankRateBenchmark;
import com.fidiacorp.credicasa.domain.model.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Driver 2 & Driver 3: Rendimiento (@Cacheable sub-100ms) e Interoperabilidad (ASR-PERF / ASR-MOD)
 * Adaptador que simula la consulta al boletín estadístico y regulatorio de la SBS
 * (Superintendencia de Banca, Seguros y AFP del Perú).
 * Implementa caché en memoria para responder de inmediato (< 100 ms) en consultas recurrentes.
 */
@Component
public class SbsAdapter {

    private static final Logger log = LoggerFactory.getLogger(SbsAdapter.class);

    // Formato externo publicado en el portal de series estadísticas SBS
    public record SbsStatisticalRecord(
            String entidad_supervisada,
            String codigo_sbs,
            String tipo_credito,
            double tasa_promedio_sistema_pen,
            double tasa_promedio_sistema_usd,
            double tasa_minima,
            double tasa_maxima,
            String fecha_publicacion
    ) {}

    /**
     * Consulta los benchmarks de tasas SBS para la moneda solicitada.
     * El resultado se almacena en la caché 'sbsRates' garantizando latencia sub-100 ms.
     */
    @Cacheable(value = "sbsRates", key = "#currency")
    public List<BankRateBenchmark> fetchOfficialBenchmarks(Currency currency) {
        log.info("[SBS-CACHE-MISS] Consultando y procesando boletín oficial de tasas SBS para moneda: {}", currency);

        // Simula la latencia de red contra el servicio externo SBS en la primera llamada sin caché
        try {
            Thread.sleep(60); // 60ms simulados de I/O externo
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        List<SbsStatisticalRecord> records = getExternalSbsRecords();
        List<BankRateBenchmark> benchmarks = new ArrayList<>();

        for (SbsStatisticalRecord record : records) {
            double avgRate = currency == Currency.USD ? record.tasa_promedio_sistema_usd() : record.tasa_promedio_sistema_pen();
            double minRate = record.tasa_minima();
            double maxRate = record.tasa_maxima();

            benchmarks.add(new BankRateBenchmark(
                    record.codigo_sbs(),
                    record.entidad_supervisada(),
                    BigDecimal.valueOf(minRate).setScale(4, RoundingMode.HALF_EVEN),
                    BigDecimal.valueOf(maxRate).setScale(4, RoundingMode.HALF_EVEN),
                    BigDecimal.valueOf(avgRate).setScale(4, RoundingMode.HALF_EVEN),
                    currency,
                    record.tipo_credito(),
                    300,
                    new BigDecimal("0.90"),
                    LocalDateTime.now()
            ));
        }

        log.info("[SBS-CACHE-STORED] Benchmarks procesados y cacheados exitosamente ({} entidades)", benchmarks.size());
        return benchmarks;
    }

    private List<SbsStatisticalRecord> getExternalSbsRecords() {
        return List.of(
                new SbsStatisticalRecord("Banco de Crédito del Perú (BCP)", "BCP", "Crédito Hipotecario", 0.0885, 0.0760, 0.0790, 0.1050, "2026-10-01"),
                new SbsStatisticalRecord("Interbank", "INTERBANK", "Crédito Hipotecario", 0.0870, 0.0750, 0.0780, 0.1040, "2026-10-01"),
                new SbsStatisticalRecord("BBVA Perú", "BBVA", "Crédito Hipotecario", 0.0890, 0.0770, 0.0800, 0.1060, "2026-10-01"),
                new SbsStatisticalRecord("Scotiabank Perú", "SCOTIABANK", "Crédito Hipotecario", 0.0910, 0.0785, 0.0810, 0.1080, "2026-10-01"),
                new SbsStatisticalRecord("BanBif", "BANBIF", "Crédito Hipotecario", 0.0925, 0.0800, 0.0825, 0.1100, "2026-10-01")
        );
    }
}
