package com.fidiacorp.credicasa.domain.service;
import com.fasterxml.jackson.databind.*;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.ports.in.CalculateScheduleCommand;
import org.junit.jupiter.api.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.*;
import static org.assertj.core.api.Assertions.*;
/** Independent Decimal(300) fixtures use exp(ln(1+TEA)/12), not the Java root algorithm. */
class FinanceReferenceTest {
    private BigDecimal number(JsonNode n,String key) {return new BigDecimal(n.get(key).asText());}
    @TestFactory Stream<DynamicTest> independentReferenceSchedules()throws Exception {
        JsonNode fixtures=new ObjectMapper().readTree(getClass().getResourceAsStream("/finance_reference.json"));
        return StreamSupport.stream(fixtures.spliterator(),false).map(f -> DynamicTest.dynamicTest(f.get("name").asText(),() -> verify(f)));
    }
    private void verify(JsonNode fixture) {
        JsonNode input=fixture.get("input");int months=input.get("months").asInt(),graceMonths=input.get("graceMonths").asInt();
        BigDecimal balloon=number(input,"balloon"),loan=number(input,"principal"),tea=number(input,"tea");
        BigDecimal cok=input.get("cok").isNull()?null:number(input,"cok");
        var quote=new FrenchAmortizationEngine().generateQuotation(new CalculateScheduleCommand(loan,Currency.PEN,months,tea,
            GracePeriod.of(GracePeriodType.valueOf(input.get("graceType").asText()),graceMonths),
            balloon.signum()>0?BalloonPayment.of(months,balloon):BalloonPayment.none(),number(input,"lifeRate"),number(input,"propertyRate"),
            number(input,"propertyValue"),number(input,"monthlyFee"),LocalDate.of(2026,1,31),null,null,cok));
        assertThat(quote.getSchedule()).hasSize(months);
        for(int i=0;i<months;i++) {
            Installment actual=quote.getSchedule().get(i);JsonNode expected=fixture.get("rows").get(i);
            assertThat(actual.getInstallmentNumber()).isEqualTo(i+1);
            assertThat(actual.getDueDate()).isEqualTo(LocalDate.of(2026,1,31).plusMonths(i+1));
            compare(actual.getInitialBalance(),expected,"initial");compare(actual.getInterest(),expected,"interest");
            compare(actual.getPrincipalAmortization(),expected,"principal");compare(actual.getInstallmentAmount(),expected,"payment");
            compare(actual.getLifeInsurance(),expected,"life");compare(actual.getPropertyInsurance(),expected,"insurance");
            compare(actual.getAdminFee(),expected,"admin");compare(actual.getFinalBalance(),expected,"final");compare(actual.getTotalPayment(),expected,"total");
        }
        assertThat(quote.getSchedule().getLast().getFinalBalance()).isEqualByComparingTo("0.00");
        FinancialMetrics m=quote.getMetrics();JsonNode sums=fixture.get("sums");
        compare(m.getTotalInterest(),sums,"interest");compare(m.getTotalPrincipal(),sums,"principal");
        compare(m.getTotalLifeInsurance(),sums,"life");compare(m.getTotalPropertyInsurance(),sums,"insurance");
        compare(m.getTotalAdminFee(),sums,"admin");compare(m.getTotalCost(),sums,"total");compare(m.getVan(),fixture,"van");
        close(m.getTem(),number(fixture,"tem"));close(m.getMonthlyTir(),number(fixture,"monthlyIrr"));close(m.getTcea(),number(fixture,"tcea"));
        if(cok!=null && cok.signum()==0)assertThat(m.getVan()).isEqualByComparingTo(loan.subtract(m.getTotalCost()));
    }
    private void compare(BigDecimal actual,JsonNode expected,String key) {assertThat(actual).isEqualByComparingTo(number(expected,key));}
    private void close(BigDecimal actual,BigDecimal expected) {
        assertThat(actual.subtract(expected).abs()).isLessThanOrEqualTo(expected.abs().max(BigDecimal.ONE).multiply(new BigDecimal("1e-24")));
    }
}
