package com.fidiacorp.credicasa.domain.service;
import com.fidiacorp.credicasa.domain.model.*;
import java.math.*;
import java.util.List;
/** Monetary calculations use DECIMAL128; IRR is solved on normalized debtor cash flows. */
public class FinancialMetricsCalculator {
    private static final MathContext MC=MathContext.DECIMAL128;
    private static final BigDecimal EPS=new BigDecimal("1e-28");
    private static final RoundingMode ROUND=RoundingMode.HALF_EVEN;
    public BigDecimal calculateTemFromTea(BigDecimal tea) {
        if(tea==null || tea.signum()<0 || tea.compareTo(BigDecimal.ONE)>0)throw new IllegalArgumentException("TEA inválida.");
        if(tea.signum()==0)return BigDecimal.ZERO.setScale(24);
        BigDecimal value=BigDecimal.ONE.add(tea),root=BigDecimal.ONE;
        for(int i=0;i<100;i++) {
            BigDecimal next=root.multiply(BigDecimal.valueOf(11),MC)
                .add(value.divide(root.pow(11,MC),MC),MC).divide(BigDecimal.valueOf(12),MC);
            if(next.subtract(root).abs().compareTo(new BigDecimal("1e-32"))<=0) {root=next;break;}
            root=next;
        }
        return root.subtract(BigDecimal.ONE,MC).setScale(24,ROUND);
    }
    public FinancialMetrics calculateMetrics(BigDecimal loan,BigDecimal tea,BigDecimal tem,List<Installment> rows,BigDecimal discountRateAnnual) {
        if(rows==null || rows.isEmpty())throw new IllegalArgumentException("El cronograma no puede estar vacío.");
        BigDecimal interest=BigDecimal.ZERO,principal=BigDecimal.ZERO,life=BigDecimal.ZERO,property=BigDecimal.ZERO,fees=BigDecimal.ZERO,total=BigDecimal.ZERO;
        BigDecimal[] payments=new BigDecimal[rows.size()];
        for(int i=0;i<rows.size();i++) {
            Installment row=rows.get(i);payments[i]=row.getTotalPayment().divide(loan,MC);
            interest=interest.add(row.getInterest());principal=principal.add(row.getPrincipalAmortization());
            life=life.add(row.getLifeInsurance());property=property.add(row.getPropertyInsurance());
            fees=fees.add(row.getAdminFee());total=total.add(row.getTotalPayment());
        }
        BigDecimal irr=solveIrr(payments,tem);
        BigDecimal tcea=BigDecimal.ONE.add(irr,MC).pow(12,MC).subtract(BigDecimal.ONE,MC).setScale(24,ROUND);
        BigDecimal cok=discountRateAnnual==null?tea:discountRateAnnual;
        BigDecimal monthlyCok=calculateTemFromTea(cok),factor=BigDecimal.ONE,base=BigDecimal.ONE.add(monthlyCok,MC),van=loan;
        for(Installment row:rows) {
            factor=factor.multiply(base,MC);van=van.subtract(row.getTotalPayment().divide(factor,MC),MC);
        }
        return new FinancialMetrics(tea.setScale(8,ROUND),tem,irr.setScale(24,ROUND),tcea,van.setScale(2,ROUND),
            interest.setScale(2,ROUND),principal.setScale(2,ROUND),life.setScale(2,ROUND),property.setScale(2,ROUND),
            fees.setScale(2,ROUND),total.setScale(2,ROUND),cok.setScale(8,ROUND));
    }
    private BigDecimal npv(BigDecimal[] payments,BigDecimal rate) {
        BigDecimal factor=BigDecimal.ONE,base=BigDecimal.ONE.add(rate,MC),value=BigDecimal.ONE;
        for(BigDecimal payment:payments) {
            factor=factor.multiply(base,MC);value=value.subtract(payment.divide(factor,MC),MC);
        }
        return value;
    }
    private BigDecimal solveIrr(BigDecimal[] payments,BigDecimal guess) {
        BigDecimal low=BigDecimal.ZERO,atZero=npv(payments,low);
        if(atZero.abs().compareTo(EPS)<=0)return BigDecimal.ZERO;
        if(atZero.signum()>0)throw new IllegalArgumentException("Los flujos no permiten una TIR no negativa.");
        BigDecimal high=guess.max(new BigDecimal("0.01"));
        int expansions=0;
        while(npv(payments,high).signum()<0 && expansions++<256)high=high.multiply(BigDecimal.valueOf(2),MC);
        if(expansions>=256)throw new IllegalArgumentException("No se pudo acotar la TIR de los flujos.");
        for(int i=0;i<256;i++) {
            BigDecimal mid=low.add(high,MC).divide(BigDecimal.valueOf(2),MC),value=npv(payments,mid);
            if(value.abs().compareTo(EPS)<=0 || high.subtract(low).abs().compareTo(EPS.multiply(mid.abs().max(BigDecimal.ONE),MC))<=0)return mid;
            if(value.signum()<0)low=mid;else high=mid;
        }
        throw new IllegalArgumentException("La TIR no convergió.");
    }
}
