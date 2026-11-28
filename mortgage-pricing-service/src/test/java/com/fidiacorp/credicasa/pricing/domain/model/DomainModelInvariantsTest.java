package com.fidiacorp.credicasa.pricing.domain.model;
import com.fidiacorp.credicasa.shared.money.Currency;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes, valores por defecto e igualdad de los value objects y agregados del dominio (sin Spring). */
class DomainModelInvariantsTest {

    @Test
    @DisplayName("GracePeriod: NONE por defecto, meses no negativos, actividad por cuota e igualdad por valor")
    void gracePeriod() {
        assertThat(new GracePeriod(null, 0).getType()).isEqualTo(GracePeriodType.NONE);
        assertThatThrownBy(() -> GracePeriod.of(GracePeriodType.TOTAL, -1)).isInstanceOf(IllegalArgumentException.class);
        GracePeriod partial = GracePeriod.of(GracePeriodType.PARTIAL, 3);
        assertThat(partial.isGraceActiveAt(3)).isTrue();
        assertThat(partial.isGraceActiveAt(4)).isFalse();
        assertThat(GracePeriod.none().isGraceActiveAt(1)).isFalse();
        assertThat(partial).isEqualTo(partial).isEqualTo(GracePeriod.of(GracePeriodType.PARTIAL, 3))
                .hasSameHashCodeAs(GracePeriod.of(GracePeriodType.PARTIAL, 3))
                .isNotEqualTo(GracePeriod.of(GracePeriodType.PARTIAL, 2))
                .isNotEqualTo(GracePeriod.of(GracePeriodType.TOTAL, 3))
                .isNotEqualTo(null).isNotEqualTo("PARTIAL");
    }

    @Test
    @DisplayName("BalloonPayment: importe nulo → 0, configurado solo con importe y cuota positivos, igualdad por valor")
    void balloonPayment() {
        assertThat(new BalloonPayment(5, null).getAmount()).isEqualByComparingTo("0");
        assertThat(BalloonPayment.none().isConfigured()).isFalse();
        assertThat(BalloonPayment.of(0, BigDecimal.TEN).isConfigured()).isFalse();
        assertThat(BalloonPayment.of(12, BigDecimal.ZERO).isConfigured()).isFalse();
        BalloonPayment balloon = BalloonPayment.of(12, BigDecimal.TEN);
        assertThat(balloon.isConfigured()).isTrue();
        assertThat(balloon).isEqualTo(balloon).isEqualTo(BalloonPayment.of(12, BigDecimal.TEN)).hasSameHashCodeAs(BalloonPayment.of(12, BigDecimal.TEN))
                .isNotEqualTo(BalloonPayment.of(11, BigDecimal.TEN)).isNotEqualTo(BalloonPayment.of(12, BigDecimal.ONE))
                .isNotEqualTo(null).isNotEqualTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("ClientProfile y PropertySnapshot: igualdad por identidad de negocio y valores por defecto")
    void profilesAndSnapshots() {
        UUID id = UUID.randomUUID();
        ClientProfile a = new ClientProfile(id, "12345678", "Ana", "ana@example.com", BigDecimal.TEN, 700);
        assertThat(a.getId()).isEqualTo(id);
        assertThat(a.getDocumentNumber()).isEqualTo("12345678");
        assertThat(a.getFullName()).isEqualTo("Ana");
        assertThat(a.getMonthlyIncome()).isEqualByComparingTo("10");
        assertThat(a.getCreditScore()).isEqualTo(700);
        assertThat(a).isEqualTo(a).isEqualTo(new ClientProfile(UUID.randomUUID(), "12345678", "Otro", "ana@example.com", null, null))
                .hasSameHashCodeAs(new ClientProfile(null, "12345678", null, "ana@example.com", null, null))
                .isNotEqualTo(new ClientProfile(id, "87654321", "Ana", "ana@example.com", null, null))
                .isNotEqualTo(new ClientProfile(id, "12345678", "Ana", "otra@example.com", null, null))
                .isNotEqualTo(null).isNotEqualTo("Ana");

        PropertySnapshot defaults = new PropertySnapshot(id, null, null, "Lima");
        assertThat(defaults.getPropertyValue()).isEqualByComparingTo("0");
        assertThat(defaults.getCurrency()).isEqualTo(Currency.PEN);
        assertThat(defaults.getAddress()).isEqualTo("Lima");
        assertThat(defaults).isEqualTo(defaults).isEqualTo(new PropertySnapshot(id, BigDecimal.ONE, Currency.USD, "Otro"))
                .hasSameHashCodeAs(new PropertySnapshot(id, null, null, null))
                .isNotEqualTo(new PropertySnapshot(UUID.randomUUID(), null, null, null)).isNotEqualTo(null).isNotEqualTo(id);
    }

    @Test
    @DisplayName("Quotation: valores por defecto ante nulos y transiciones de estado")
    void quotationDefaultsAndStatus() {
        Quotation q = new Quotation(null, BigDecimal.TEN, null, 12, new BigDecimal("0.1"), null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        assertThat(q.getId()).isNotNull();
        assertThat(q.getCurrency()).isEqualTo(Currency.PEN);
        assertThat(q.getGracePeriod()).isEqualTo(GracePeriod.none());
        assertThat(q.getBalloonPayment()).isEqualTo(BalloonPayment.none());
        assertThat(q.getLifeInsuranceRate()).isEqualByComparingTo("0");
        assertThat(q.getPropertyInsuranceRate()).isEqualByComparingTo("0");
        assertThat(q.getPropertyValue()).isEqualByComparingTo("0");
        assertThat(q.getMonthlyAdminFee()).isEqualByComparingTo("0");
        assertThat(q.getStartDate()).isNotNull();
        assertThat(q.getSchedule()).isEmpty();
        assertThat(q.getStatus()).isEqualTo(QuotationStatus.ISSUED);
        assertThat(q.getCreatedAt()).isNotNull();
        assertThat(q.getOwnerId()).isNull();
        assertThat(q.getAnnualInterestRate()).isEqualByComparingTo("0.1");
        q.markAsAccepted();
        assertThat(q.getStatus()).isEqualTo(QuotationStatus.ACCEPTED);
        q.markAsExpired();
        assertThat(q.getStatus()).isEqualTo(QuotationStatus.EXPIRED);
        assertThatThrownBy(() -> q.getSchedule().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
