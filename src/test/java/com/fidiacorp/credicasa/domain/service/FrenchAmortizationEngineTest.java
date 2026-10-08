package com.fidiacorp.credicasa.domain.service;

import com.fidiacorp.credicasa.domain.model.BalloonPayment;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.GracePeriod;
import com.fidiacorp.credicasa.domain.model.GracePeriodType;
import com.fidiacorp.credicasa.domain.model.Installment;
import com.fidiacorp.credicasa.domain.model.Quotation;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Driver 2 (ASR-PERF): Pruebas Unitarias del Motor Francés y Precisión Financiera")
class FrenchAmortizationEngineTest {

    private FrenchAmortizationEngine engine;
    private FinancialMetricsCalculator metricsCalculator;

    @BeforeEach
    void setUp() {
        metricsCalculator = new FinancialMetricsCalculator();
        engine = new FrenchAmortizationEngine(metricsCalculator);
    }

    @Test
    @DisplayName("Escenario Mandatorio: Crédito de S/. 200,000 a 20 años liquida saldo insoluto a 0.00 exacto")
    void testStandardCredit200k20YearsFinalBalanceIsZero() {
        // Arrange: Crédito hipotecario estándar peruano
        BigDecimal loanAmount = new BigDecimal("200000.00");
        BigDecimal propertyValue = new BigDecimal("250000.00");
        int termYears = 20;
        int termMonths = termYears * 12; // 240 meses
        BigDecimal tea = new BigDecimal("0.085000"); // 8.50% TEA
        BigDecimal lifeInsuranceRate = new BigDecimal("0.000500"); // 0.05% mensual sobre saldo
        BigDecimal propertyInsuranceRate = new BigDecimal("0.000250"); // 0.025% mensual sobre valor del bien
        BigDecimal monthlyAdminFee = new BigDecimal("10.00"); // Portes y gastos

        CalculateScheduleCommand command = new CalculateScheduleCommand(
                loanAmount,
                Currency.PEN,
                termMonths,
                tea,
                GracePeriod.none(),
                BalloonPayment.none(),
                lifeInsuranceRate,
                propertyInsuranceRate,
                propertyValue,
                monthlyAdminFee,
                LocalDate.of(2026, 11, 1),
                null,
                null,
                tea
        );

        // Act: Ejecutar motor financiero
        Quotation quotation = engine.generateQuotation(command);

        // Assert: Validaciones matemáticas estrictas
        assertNotNull(quotation, "La cotización no debe ser nula");
        List<Installment> schedule = quotation.getSchedule();
        assertEquals(240, schedule.size(), "El cronograma debe contener exactamente 240 cuotas");

        BigDecimal totalAmortization = BigDecimal.ZERO;
        for (int i = 0; i < schedule.size(); i++) {
            Installment inst = schedule.get(i);
            assertEquals(i + 1, inst.getInstallmentNumber(), "Correlativo de cuota inválido");

            // Coherencia contable en cada período
            BigDecimal expectedBalance = inst.getInitialBalance().subtract(inst.getPrincipalAmortization());
            assertEquals(
                    inst.getFinalBalance(),
                    expectedBalance,
                    "En la cuota " + (i + 1) + " el saldo final debe ser initialBalance - principalAmortization"
            );

            // Cuota pura = interés + amortización
            assertEquals(
                    inst.getInstallmentAmount(),
                    inst.getInterest().add(inst.getPrincipalAmortization()),
                    "En la cuota " + (i + 1) + " la cuota pura debe ser interés + amortización"
            );

            // Pago total = cuota pura + seguro desgravamen + seguro inmueble + portes
            BigDecimal expectedTotal = inst.getInstallmentAmount()
                    .add(inst.getLifeInsurance())
                    .add(inst.getPropertyInsurance())
                    .add(inst.getAdminFee());
            assertEquals(
                    inst.getTotalPayment(),
                    expectedTotal,
                    "En la cuota " + (i + 1) + " el pago total debe coincidir con la suma de componentes"
            );

            totalAmortization = totalAmortization.add(inst.getPrincipalAmortization());
        }

        // VALIDACIÓN CRÍTICA DEL DRIVER 2 & AC-02:
        // 1. El saldo final de la última cuota (mes 240) debe ser EXACTAMENTE 0.00
        Installment lastInstallment = schedule.get(239);
        assertEquals(
                new BigDecimal("0.00"),
                lastInstallment.getFinalBalance(),
                "El saldo final de la última cuota debe ser 0.00 exacto según el criterio AC-02"
        );

        // 2. La suma de todas las amortizaciones debe liquidar íntegramente el préstamo original
        assertEquals(
                loanAmount,
                totalAmortization.setScale(2, RoundingMode.HALF_EVEN),
                "La suma total de amortizaciones debe igualar al préstamo de S/. 200,000.00 exacto"
        );

        // 3. Verificación de Métricas Financieras (TCEA > TEA debido a seguros y cargos)
        assertNotNull(quotation.getMetrics());
        assertTrue(quotation.getMetrics().getTcea().compareTo(quotation.getMetrics().getTea()) > 0,
                "La TCEA debe ser mayor a la TEA al incluir seguros y cargos administrativos");
        assertTrue(quotation.getMetrics().getMonthlyTir().compareTo(BigDecimal.ZERO) > 0,
                "La TIR mensual debe ser positiva");
    }

    @Test
    @DisplayName("Soporte de Período de Gracia Total (6 meses) con saldo final 0.00 exacto")
    void testCreditWithTotalGracePeriod() {
        BigDecimal loanAmount = new BigDecimal("150000.00");
        int termMonths = 120; // 10 años
        BigDecimal tea = new BigDecimal("0.090000"); // 9% TEA
        int graceMonths = 6;

        CalculateScheduleCommand command = new CalculateScheduleCommand(
                loanAmount,
                Currency.PEN,
                termMonths,
                tea,
                GracePeriod.of(GracePeriodType.TOTAL, graceMonths),
                BalloonPayment.none(),
                new BigDecimal("0.0005"),
                new BigDecimal("0.0002"),
                loanAmount,
                BigDecimal.ZERO,
                LocalDate.now(),
                null,
                null,
                tea
        );

        Quotation quotation = engine.generateQuotation(command);
        List<Installment> schedule = quotation.getSchedule();

        assertEquals(120, schedule.size());

        // En los primeros 6 meses (gracia total), la amortización debe ser 0.00 y el saldo debe crecer
        for (int i = 0; i < graceMonths; i++) {
            Installment inst = schedule.get(i);
            assertEquals(GracePeriodType.TOTAL, inst.getGraceType());
            assertEquals(new BigDecimal("0.00"), inst.getPrincipalAmortization());
            assertEquals(new BigDecimal("0.00"), inst.getInstallmentAmount());
            assertTrue(inst.getFinalBalance().compareTo(inst.getInitialBalance()) > 0,
                    "En gracia total el interés se capitaliza y el saldo aumenta");
        }

        // Última cuota liquida a 0.00
        Installment last = schedule.get(119);
        assertEquals(new BigDecimal("0.00"), last.getFinalBalance());
    }

    @Test
    @DisplayName("Soporte de Período de Gracia Parcial (3 meses) con saldo final 0.00 exacto")
    void testCreditWithPartialGracePeriod() {
        BigDecimal loanAmount = new BigDecimal("100000.00");
        int termMonths = 60; // 5 años
        BigDecimal tea = new BigDecimal("0.080000"); // 8% TEA
        int graceMonths = 3;

        CalculateScheduleCommand command = new CalculateScheduleCommand(
                loanAmount,
                Currency.PEN,
                termMonths,
                tea,
                GracePeriod.of(GracePeriodType.PARTIAL, graceMonths),
                BalloonPayment.none(),
                new BigDecimal("0.0004"),
                new BigDecimal("0.0002"),
                loanAmount,
                BigDecimal.ZERO,
                LocalDate.now(),
                null,
                null,
                tea
        );

        Quotation quotation = engine.generateQuotation(command);
        List<Installment> schedule = quotation.getSchedule();

        // En gracia parcial, amortización = 0 pero se pagan intereses, por lo que el saldo no varía
        for (int i = 0; i < graceMonths; i++) {
            Installment inst = schedule.get(i);
            assertEquals(GracePeriodType.PARTIAL, inst.getGraceType());
            assertEquals(new BigDecimal("0.00"), inst.getPrincipalAmortization());
            assertEquals(inst.getInitialBalance(), inst.getFinalBalance(), "En gracia parcial el saldo se conserva");
            assertEquals(inst.getInterest(), inst.getInstallmentAmount(), "En gracia parcial la cuota cubre exactamente el interés");
        }

        Installment last = schedule.get(59);
        assertEquals(new BigDecimal("0.00"), last.getFinalBalance());
    }

    @Test
    @DisplayName("Soporte de Cuota Balón al final del período con saldo final 0.00 exacto")
    void testCreditWithBalloonPayment() {
        BigDecimal loanAmount = new BigDecimal("180000.00");
        int termMonths = 120;
        BigDecimal tea = new BigDecimal("0.085000");
        BigDecimal balloonAmount = new BigDecimal("25000.00");

        CalculateScheduleCommand command = new CalculateScheduleCommand(
                loanAmount,
                Currency.PEN,
                termMonths,
                tea,
                GracePeriod.none(),
                BalloonPayment.of(termMonths, balloonAmount),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                loanAmount,
                BigDecimal.ZERO,
                LocalDate.now(),
                null,
                null,
                tea
        );

        Quotation quotation = engine.generateQuotation(command);
        List<Installment> schedule = quotation.getSchedule();

        Installment last = schedule.get(119);
        assertEquals(new BigDecimal("0.00"), last.getFinalBalance());
        assertTrue(last.getPrincipalAmortization().compareTo(balloonAmount) >= 0,
                "La amortización de la última cuota debe absorber el balón programado");
    }
}
