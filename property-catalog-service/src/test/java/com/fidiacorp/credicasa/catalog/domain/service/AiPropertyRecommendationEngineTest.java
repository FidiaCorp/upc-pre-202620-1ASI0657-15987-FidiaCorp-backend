package com.fidiacorp.credicasa.catalog.domain.service;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.catalog.domain.model.AiPropertyRecommendation;
import com.fidiacorp.credicasa.catalog.domain.model.AiRecommendationCriteria;
import com.fidiacorp.credicasa.catalog.domain.model.Property;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US15: motor determinista en BigDecimal puro (sin double ni Math.pow) y sin mezclar monedas. */
class AiPropertyRecommendationEngineTest {
    private final AiPropertyRecommendationEngine engine = new AiPropertyRecommendationEngine();

    private static Property property(String title, String address, String price, Currency currency, String... tags) {
        return new Property(UUID.randomUUID(), UUID.randomUUID(), title, address, "desc", new BigDecimal(price), currency,
                new HashSet<>(Arrays.asList(tags)), Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    @DisplayName("Cuota estimada = cuota francesa del motor financiero (DECIMAL128, HALF_EVEN) a TEA 9.50%")
    void installmentMatchesFrenchEngineExactly() {
        Property p = property("Depa", "Av. Larco, Miraflores", "280000.00", Currency.PEN);
        var criteria = new AiRecommendationCriteria(new BigDecimal("6000.00"), new BigDecimal("50000.00"), Currency.PEN, null, 20, null);
        AiPropertyRecommendation r = engine.rankProperties(List.of(p), criteria).get(0);
        BigDecimal expected = com.fidiacorp.credicasa.shared.finance.FinancialMath.levelInstallment(new BigDecimal("230000.00"), com.fidiacorp.credicasa.shared.finance.FinancialMath.monthlyRateFromAnnualEffective(new BigDecimal("0.0950")), 240);
        assertThat(r.getEstimatedMonthlyInstallment()).isEqualByComparingTo(expected).isEqualByComparingTo("2085.65");
        assertThat(r.getEstimatedMonthlyInstallment().scale()).isEqualTo(2);
        assertThat(r.getDebtToIncomeRatioPercent()).isEqualByComparingTo("34.76");
        assertThat(r.isFinanciallyViable()).isTrue();
        assertThat(r.getRelevanceScore()).isEqualTo(50); // 30 (viable) + 20 (sin distrito)
        assertThat(engine.maxAllowableInstallment(new BigDecimal("6000.00"))).isEqualByComparingTo("2100.00");
    }

    @Test
    @DisplayName("Filtra por moneda: nunca compara un inmueble USD con un ingreso en PEN")
    void filtersByCurrency() {
        List<Property> catalogue = List.of(property("Soles", "Surco", "300000.00", Currency.PEN),
                property("Dolares", "Surco", "100000.00", Currency.USD));
        var pen = new AiRecommendationCriteria(new BigDecimal("8000"), null, Currency.PEN, "", 20, List.of());
        var usd = new AiRecommendationCriteria(new BigDecimal("8000"), null, Currency.USD, "", 20, List.of());
        assertThat(engine.rankProperties(catalogue, pen)).extracting(AiPropertyRecommendation::getTitle).containsExactly("Soles");
        assertThat(engine.rankProperties(catalogue, usd)).extracting(AiPropertyRecommendation::getTitle).containsExactly("Dolares");
        assertThat(engine.rankProperties(catalogue, new AiRecommendationCriteria(new BigDecimal("8000"), null, null, null, 0, null)))
                .as("moneda por defecto PEN").extracting(AiPropertyRecommendation::getCurrency).containsOnly(Currency.PEN);
    }

    @Test
    @DisplayName("Puntuación: ratio ideal, distrito, etiquetas (tope 25) y alerta de sobreendeudamiento; orden descendente")
    void scoresAndRanks() {
        Property ideal = property("Ideal", "Calle Berlin 450, MIRAFLORES", "150000.00", Currency.PEN, "terraza", "estreno", "vista-parque");
        Property elsewhere = property("Otro distrito", "Av. Primavera, Surco", "150000.00", Currency.PEN);
        Property expensive = property("Mansion", "La Molina", "3500000.00", Currency.PEN, "piscina");
        var criteria = new AiRecommendationCriteria(new BigDecimal("6000.00"), new BigDecimal("50000.00"), Currency.PEN,
                " Miraflores ", 30, Arrays.asList("terraza", "ESTRENO", "vista", " ", null));
        List<AiPropertyRecommendation> ranked = engine.rankProperties(List.of(expensive, elsewhere, ideal), criteria);
        assertThat(ranked).extracting(AiPropertyRecommendation::getTitle).containsExactly("Ideal", "Otro distrito", "Mansion");
        assertThat(ranked.get(0).getRelevanceScore()).isEqualTo(100); // 45 + 30 + min(25, 3*10)
        assertThat(ranked.get(0).getRecommendationReason()).contains("Excelente viabilidad").contains("distrito preferido").contains("3 atributo");
        assertThat(ranked.get(1).getRelevanceScore()).isEqualTo(55); // 45 + 10
        assertThat(ranked.get(2).isFinanciallyViable()).isFalse();
        assertThat(ranked.get(2).getRelevanceScore()).isEqualTo(15); // 5 + 10
        assertThat(ranked.get(2).getRecommendationReason()).contains("sobreendeudamiento");
    }

    @Test
    @DisplayName("Cuota inicial mayor al precio → préstamo 0.00 y ratio 0.00")
    void downPaymentCoveringPriceMeansNoLoan() {
        var criteria = new AiRecommendationCriteria(new BigDecimal("3000"), new BigDecimal("999999"), Currency.PEN, "", 20, List.of());
        AiPropertyRecommendation r = engine.rankProperties(List.of(property("Barata", "Lima", "90000.00", Currency.PEN)), criteria).get(0);
        assertThat(r.getEstimatedMonthlyInstallment()).isEqualByComparingTo("0.00");
        assertThat(r.getDebtToIncomeRatioPercent()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Criterios: ingreso obligatorio y positivo; cuota inicial negativa → 0; plazo fuera de 1..30 → 20 años")
    void criteriaDefaults() {
        assertThatThrownBy(() -> new AiRecommendationCriteria(null, null, null, null, 0, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AiRecommendationCriteria(BigDecimal.ZERO, null, null, null, 0, null)).isInstanceOf(IllegalArgumentException.class);
        var c = new AiRecommendationCriteria(BigDecimal.TEN, new BigDecimal("-5"), null, null, 45, null);
        assertThat(c.getDownPaymentAvailable()).isEqualByComparingTo("0");
        assertThat(c.getDesiredTermYears()).isEqualTo(20);
        assertThat(c.getPreferredDistrict()).isEmpty();
        assertThat(c.getPreferenceTags()).isEmpty();
        assertThat(new AiPropertyRecommendationEngine(null).maxAllowableInstallment(new BigDecimal("1000"))).isEqualByComparingTo("350.00");
    }

    @Test
    @DisplayName("PA-02/CON-04: el código fuente del motor no usa double ni Math.pow ni anotaciones Spring")
    void sourceUsesNoFloatingPointNorSpring() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fidiacorp/credicasa/catalog/domain/service/AiPropertyRecommendationEngine.java"));
        String code = source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        assertThat(code).doesNotContain("double").doesNotContain("Math.pow").doesNotContain("@Service").doesNotContain("springframework");
    }
}
