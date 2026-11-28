package com.fidiacorp.credicasa.pricing.domain.service;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fidiacorp.credicasa.pricing.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriod;
import com.fidiacorp.credicasa.pricing.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.pricing.domain.ports.in.CalculateScheduleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas de validación del motor financiero (AC-02, PA-02): cada combinación inválida se rechaza antes de calcular. */
class SimulationRulesTest {
    private record Draft(BigDecimal loan, Currency currency, int term, BigDecimal tea, GracePeriod grace, BalloonPayment balloon,
                         BigDecimal life, BigDecimal insurance, BigDecimal property, BigDecimal fee, LocalDate start, BigDecimal cok) {
        CalculateScheduleCommand command() {
            return new CalculateScheduleCommand(loan, currency, term, tea, grace, balloon, life, insurance, property, fee, start, null, null, cok);
        }
    }

    private static final Draft VALID = new Draft(new BigDecimal("100000.00"), Currency.PEN, 120, new BigDecimal("0.095"), GracePeriod.none(),
            BalloonPayment.none(), new BigDecimal("0.0005"), new BigDecimal("0.0002"), new BigDecimal("150000.00"), new BigDecimal("10.00"),
            LocalDate.of(2026, 1, 1), new BigDecimal("0.10"));

    private static void rejects(UnaryOperator<Draft> change) {
        assertThatThrownBy(() -> SimulationRules.validate(change.apply(VALID).command())).isInstanceOf(IllegalArgumentException.class);
    }

    private static void accepts(UnaryOperator<Draft> change) {
        assertThatCode(() -> SimulationRules.validate(change.apply(VALID).command())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Acepta comandos válidos, incluidos opcionales nulos, gracia y balón al vencimiento")
    void acceptsValidCommands() {
        accepts(d -> d);
        accepts(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), null, null, null, null, null, null, null, null));
        accepts(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), GracePeriod.of(GracePeriodType.TOTAL, 6), BalloonPayment.of(120, new BigDecimal("1000.00")),
                BigDecimal.ZERO, BigDecimal.ZERO, d.property(), BigDecimal.ZERO, d.start(), BigDecimal.ZERO));
        accepts(d -> new Draft(new BigDecimal("1.00"), d.currency(), 1, new BigDecimal("0.00000001"), d.grace(), d.balloon(), BigDecimal.ONE, BigDecimal.ONE,
                null, d.fee(), LocalDate.of(1900, 1, 1), BigDecimal.ONE));
    }

    @Test
    @DisplayName("Rechaza nulos obligatorios, importes y tasas fuera de rango o precisión")
    void rejectsInvalidAmountsAndRates() {
        assertThatThrownBy(() -> SimulationRules.validate(null)).isInstanceOf(IllegalArgumentException.class);
        rejects(d -> new Draft(null, d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(BigDecimal.ZERO, d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(new BigDecimal("-1"), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(new BigDecimal("1.001"), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(new BigDecimal("12345678901234567.00"), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), null, d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(new BigDecimal("200000.00"), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), null, d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), 0, d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), 361, d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), null, d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), BigDecimal.ZERO, d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), new BigDecimal("1.01"), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), new BigDecimal("0.123456789"), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), new BigDecimal("-0.01"), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), new BigDecimal("2"), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), new BigDecimal("-1"), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), new BigDecimal("-0.1")));
    }

    @Test
    @DisplayName("Rechaza fechas fuera de rango, gracia incoherente y balón que no vence al final o supera el préstamo")
    void rejectsDatesGraceAndBalloon() {
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), LocalDate.of(1899, 12, 31), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), LocalDate.of(9991, 1, 1), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), 360, d.tea(), d.grace(), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), LocalDate.of(9980, 1, 1), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), GracePeriod.of(GracePeriodType.NONE, 2), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), GracePeriod.of(GracePeriodType.PARTIAL, 0), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), GracePeriod.of(GracePeriodType.TOTAL, 37), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), 12, d.tea(), GracePeriod.of(GracePeriodType.TOTAL, 12), d.balloon(), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), BalloonPayment.of(5, BigDecimal.ZERO), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), BalloonPayment.of(60, BigDecimal.TEN), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), BalloonPayment.of(120, new BigDecimal("100000.01")), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
        rejects(d -> new Draft(d.loan(), d.currency(), d.term(), d.tea(), d.grace(), BalloonPayment.of(120, new BigDecimal("-5")), d.life(), d.insurance(), d.property(), d.fee(), d.start(), d.cok()));
    }
}
