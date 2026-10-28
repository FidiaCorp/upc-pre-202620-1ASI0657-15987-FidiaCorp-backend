package com.fidiacorp.credicasa.domain.service;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleCommand;
import com.fidiacorp.credicasa.domain.model.*;
import java.math.BigDecimal;
public final class SimulationRules {
    private SimulationRules() {}
    public static void validate(CalculateScheduleCommand c) {
        if(c==null)throw new IllegalArgumentException("El comando no puede ser nulo.");
        money(c.loanAmount(),true,"préstamo");
        BigDecimal property=c.propertyValue()==null?c.loanAmount():c.propertyValue();money(property,true,"inmueble");
        if(c.loanAmount().compareTo(property)>0)throw new IllegalArgumentException("El préstamo no puede superar el valor del inmueble.");
        if(c.currency()==null)throw new IllegalArgumentException("La moneda es obligatoria.");
        if(c.termMonths()<1 || c.termMonths()>360)throw new IllegalArgumentException("El plazo debe estar entre 1 y 360 meses.");
        rate(c.annualInterestRate(),true,"TEA");rate(c.lifeInsuranceRate(),false,"desgravamen");
        rate(c.propertyInsuranceRate(),false,"seguro del inmueble");rate(c.discountRateAnnual(),false,"COK");
        money(c.monthlyAdminFee(),false,"gasto administrativo");
        if(c.startDate()!=null && (c.startDate().getYear()<1900 || c.startDate().getYear()>9990))
            throw new IllegalArgumentException("La fecha inicial debe estar entre los años 1900 y 9990.");
        if(c.startDate()!=null && c.startDate().plusMonths(c.termMonths()).getYear()>9999)
            throw new IllegalArgumentException("La última cuota debe tener una fecha con año de cuatro dígitos.");
        GracePeriod grace=c.gracePeriod()==null?GracePeriod.none():c.gracePeriod();
        if(grace.getType()==GracePeriodType.NONE && grace.getMonths()!=0)
            throw new IllegalArgumentException("La gracia NONE requiere cero meses.");
        if(grace.getType()!=GracePeriodType.NONE && (grace.getMonths()<1 || grace.getMonths()>36 || grace.getMonths()>=c.termMonths()))
            throw new IllegalArgumentException("La gracia requiere entre 1 y 36 meses y debe ser menor al plazo.");
        BalloonPayment balloon=c.balloonPayment()==null?BalloonPayment.none():c.balloonPayment();
        money(balloon.getAmount(),false,"balón");
        if(balloon.getAmount().signum()==0 && balloon.getInstallmentNumber()!=0)
            throw new IllegalArgumentException("Un balón sin importe requiere mes cero.");
        if(balloon.getAmount().signum()>0 && (balloon.getInstallmentNumber()!=c.termMonths() || balloon.getAmount().compareTo(c.loanAmount())>0))
            throw new IllegalArgumentException("El balón solo puede pagarse al vencimiento y no debe superar el préstamo.");
    }
    private static void money(BigDecimal v,boolean required,String field) {
        if(v==null) {if(required)throw new IllegalArgumentException("Falta el importe de "+field);return;}
        BigDecimal n=v.stripTrailingZeros();
        if(v.signum()<0 || (required && v.signum()==0) || Math.max(0,n.scale())>2 || n.precision()-n.scale()>16)
            throw new IllegalArgumentException("Importe inválido de "+field+": máximo 16 enteros y 2 decimales, sin negativos.");
    }
    private static void rate(BigDecimal v,boolean required,String field) {
        if(v==null) {if(required)throw new IllegalArgumentException("Falta la tasa "+field);return;}
        if(v.signum()<0 || (required && v.signum()==0) || v.compareTo(BigDecimal.ONE)>0 || Math.max(0,v.stripTrailingZeros().scale())>8)
            throw new IllegalArgumentException("Tasa inválida de "+field+": decimal entre 0 y 1, máximo 8 decimales.");
    }
}
