package com.fidiacorp.credicasa.domain.service;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleCommand;
import java.math.*;
import java.time.*;
import java.util.*;
/** French amortization with cent rounding and exact final liquidation. */
public class FrenchAmortizationEngine {
    private static final MathContext MC=MathContext.DECIMAL128;
    private static final RoundingMode ROUND=RoundingMode.HALF_EVEN;
    private final FinancialMetricsCalculator metrics;
    public FrenchAmortizationEngine() {this(new FinancialMetricsCalculator());}
    public FrenchAmortizationEngine(FinancialMetricsCalculator metrics) {this.metrics=metrics==null?new FinancialMetricsCalculator():metrics;}
    private BigDecimal money(BigDecimal value) {return value.setScale(2,ROUND);}
    private BigDecimal zeroIfNull(BigDecimal value) {return value==null?BigDecimal.ZERO:value;}
    public Quotation generateQuotation(CalculateScheduleCommand c) {
        SimulationRules.validate(c);
        BigDecimal loan=money(c.loanAmount()),property=c.propertyValue()==null?loan:money(c.propertyValue());
        BigDecimal tea=c.annualInterestRate(),tem=metrics.calculateTemFromTea(tea);
        GracePeriod grace=c.gracePeriod()==null?GracePeriod.none():c.gracePeriod();
        BalloonPayment balloon=c.balloonPayment()==null?BalloonPayment.none():c.balloonPayment();
        LocalDate start=c.startDate()==null?LocalDate.now():c.startDate();
        BigDecimal life=zeroIfNull(c.lifeInsuranceRate()),insurance=zeroIfNull(c.propertyInsuranceRate()),fee=zeroIfNull(c.monthlyAdminFee());
        List<Installment> rows=calculateSchedule(loan,property,c.termMonths(),tem,start,grace,balloon,life,insurance,fee);
        return new Quotation(UUID.randomUUID(),loan,c.currency(),c.termMonths(),tea.setScale(8,ROUND),grace,balloon,
            life,insurance,property,fee,start,c.clientProfile(),c.propertySnapshot(),rows,
            metrics.calculateMetrics(loan,tea,tem,rows,c.discountRateAnnual()),QuotationStatus.ISSUED,LocalDateTime.now(),c.ownerId());
    }
    public List<Installment> calculateSchedule(BigDecimal principal,BigDecimal propertyValue,int termMonths,BigDecimal tem,
        LocalDate startDate,GracePeriod grace,BalloonPayment balloon,BigDecimal lifeRate,BigDecimal propertyRate,BigDecimal monthlyFee) {
        List<Installment> rows=new ArrayList<>(termMonths);BigDecimal balance=money(principal);
        int graceMonths=grace.getMonths();
        for(int k=1;k<=graceMonths;k++) {
            BigDecimal initial=balance,interest=money(initial.multiply(tem,MC));
            BigDecimal pure=grace.getType()==GracePeriodType.TOTAL?money(BigDecimal.ZERO):interest;
            if(grace.getType()==GracePeriodType.TOTAL)balance=balance.add(interest);
            BigDecimal life=money(initial.multiply(lifeRate,MC)),insurance=money(propertyValue.multiply(propertyRate,MC)),fee=money(monthlyFee);
            rows.add(new Installment(k,startDate.plusMonths(k),initial,interest,money(BigDecimal.ZERO),pure,life,insurance,fee,
                pure.add(life).add(insurance).add(fee),balance,grace.getType()));
        }
        int regularMonths=termMonths-graceMonths;
        BigDecimal regular=regularInstallment(balance,tem,regularMonths,balloon);
        for(int m=1;m<=regularMonths;m++) {
            int k=graceMonths+m;BigDecimal initial=balance,interest=money(initial.multiply(tem,MC));
            BigDecimal amortization=m==regularMonths?initial:regular.subtract(interest).max(BigDecimal.ZERO).min(initial);
            BigDecimal pure=amortization.add(interest);balance=money(initial.subtract(amortization));
            BigDecimal life=money(initial.multiply(lifeRate,MC)),insurance=money(propertyValue.multiply(propertyRate,MC)),fee=money(monthlyFee);
            rows.add(new Installment(k,startDate.plusMonths(k),initial,interest,amortization,pure,life,insurance,fee,
                pure.add(life).add(insurance).add(fee),balance,GracePeriodType.NONE));
        }
        return rows;
    }
    private BigDecimal regularInstallment(BigDecimal principal,BigDecimal rate,int months,BalloonPayment balloon) {
        BigDecimal future=BigDecimal.ONE.add(rate,MC).pow(months,MC);
        BigDecimal presentBalloon=balloon.isConfigured()?balloon.getAmount().divide(future,MC):BigDecimal.ZERO;
        if(rate.signum()==0)return money(principal.subtract(presentBalloon).divide(BigDecimal.valueOf(months),MC));
        BigDecimal annuity=BigDecimal.ONE.subtract(BigDecimal.ONE.divide(future,MC),MC).divide(rate,MC);
        return money(principal.subtract(presentBalloon).divide(annuity,MC));
    }
}
