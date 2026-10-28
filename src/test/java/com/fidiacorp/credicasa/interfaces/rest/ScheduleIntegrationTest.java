package com.fidiacorp.credicasa.interfaces.rest;
import com.fasterxml.jackson.databind.*;
import com.fidiacorp.credicasa.application.usecase.OfferScheduleService;
import com.fidiacorp.credicasa.domain.ports.out.QuotationRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.CreditOfferRepositoryAdapter;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:schedules;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ScheduleIntegrationTest {
    record User(UUID id,String token,String email) {}
    private static final String PASSWORD="CorrectHorseBattery123!",BASE="/api/v1/simulations";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired PropertyJpaRepository properties;
    @Autowired CreditOfferJpaRepository offers;
    @Autowired OfferNotificationJpaRepository notices;
    @Autowired QuotationJpaRepository quotations;
    @Autowired QuotationRepositoryPort quotationPort;
    @Autowired OfferScheduleService schedules;
    @SpyBean CreditOfferRepositoryAdapter offerAdapter;
    private User recipient,other,advisor;
    private String body(Object value)throws Exception{return json.writeValueAsString(value);}
    private JsonNode response(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString());}
    private User register(String email)throws Exception {
        var r=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Lucía","lastName","Pérez","email",email,"password",PASSWORD))))
            .andExpect(status().isCreated()).andReturn();
        var l=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn();
        return new User(UUID.fromString(response(r).get("id").asText()),response(l).get("token").asText(),email);
    }
    @BeforeEach void prepare()throws Exception {
        notices.deleteAll();offers.deleteAll();quotations.deleteAll();properties.deleteAll();accounts.deleteAll();
        recipient=register("owner@example.com");other=register("other@example.com");advisor=register("advisor@example.com");
        var a=accounts.findById(advisor.id()).orElseThrow();a.roles="ROLE_REALTOR";accounts.saveAndFlush(a);
    }
    private Map<String,Object> data() {
        Map<String,Object> d=new HashMap<>();d.put("loanAmount","200000.00");d.put("propertyValue","250000.00");d.put("currency","PEN");
        d.put("annualInterestRate","0.08512345");d.put("termMonths",12);d.put("startDate","2024-01-31");return d;
    }
    private ResultActions calculate(Map<String,Object> d)throws Exception {
        return mvc.perform(post(BASE+"/calculate").header("Authorization","Bearer "+recipient.token())
            .contentType(MediaType.APPLICATION_JSON).content(body(d)));
    }
    private UUID offer()throws Exception {
        var p=mvc.perform(post("/api/v1/properties").header("Authorization","Bearer "+advisor.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("title","Casa histórica","address","Av. Independencia 123","price","250000.00","currency","PEN"))))
            .andExpect(status().isCreated()).andReturn();
        String propertyId=response(p).get("id").asText();
        var o=mvc.perform(post("/api/v1/offers").header("Authorization","Bearer "+advisor.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("recipientEmail",recipient.email(),"propertyId",propertyId,"bankCode","BCP","loanAmount","200000.00",
                "currency","PEN","annualInterestRate","0.08512345","termMonths",12,"startDate","2024-01-31"))))
            .andExpect(status().isCreated()).andReturn();
        return UUID.fromString(response(o).get("id").asText());
    }
    @Test void ownsSimulationIgnoresSubmittedClientIdentityAndRoundTripsIdsAndZeroCok()throws Exception {
        var d=data();d.put("discountRateAnnual","0");d.put("clientProfile",Map.of("id",other.id(),"documentNumber","12345678",
            "fullName","Different person","email",other.email(),"monthlyIncome",10000,"creditScore",900));
        var created=response(calculate(d).andExpect(status().isCreated()).andReturn());UUID id=UUID.fromString(created.get("quotationId").asText());
        var stored=quotationPort.findById(id).orElseThrow();
        assertThat(stored.getOwnerId()).isEqualTo(recipient.id());assertThat(stored.getClientProfile().getId()).isEqualTo(recipient.id());
        assertThat(stored.getClientProfile().getEmail()).isEqualTo(recipient.email());
        assertThat(stored.getClientProfile().getFullName()).isEqualTo("Lucía Pérez");
        assertThat(stored.getMetrics().getDiscountRateUsed()).isEqualByComparingTo("0");
        assertThat(stored.getAnnualInterestRate()).isEqualByComparingTo("0.08512345");
        assertThat(quotationPort.findById(id).orElseThrow().getPropertySnapshot().getPropertyId())
            .isEqualTo(stored.getPropertySnapshot().getPropertyId()).isNotNull();
        var read=response(mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk()).andReturn());
        assertThat(read.get("metrics")).isEqualTo(created.get("metrics"));assertThat(read.get("schedule")).isEqualTo(created.get("schedule"));
        assertThat(read.get("loanAmount").isTextual()).isTrue();assertThat(read.get("metrics").get("tea").isTextual()).isTrue();
        assertThat(new BigDecimal(read.get("metrics").get("van").asText()))
            .isEqualByComparingTo(new BigDecimal("200000.00").subtract(new BigDecimal(read.get("metrics").get("totalCost").asText())));
        mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+other.token())).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/"+id)).andExpect(status().isUnauthorized());
        var legacy=quotations.findById(id).orElseThrow();legacy.setOwnerId(null);quotations.saveAndFlush(legacy);
        mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+recipient.token())).andExpect(status().isNotFound());
    }
    @Test void validatesCrossFieldsAndSafeDatesBeforePersisting()throws Exception {
        List<Map<String,Object>> changes=List.of(Map.of("loanAmount","250000.01"),Map.of("annualInterestRate","8.5"),
            Map.of("annualInterestRate","0"),Map.of("annualInterestRate","0.123456789"),Map.of("termYears",2),
            Map.of("gracePeriodType","NONE","gracePeriodMonths",2),Map.of("gracePeriodType","TOTAL"),
            Map.of("gracePeriodType","TOTAL","gracePeriodMonths",12),Map.of("gracePeriodMonths",-1),
            Map.of("balloonPaymentAmount","100","balloonPaymentMonth",6),Map.of("balloonPaymentAmount","100"),
            Map.of("balloonPaymentAmount","200001","balloonPaymentMonth",12),Map.of("balloonPaymentMonth",12),
            Map.of("monthlyAdminFee","-1"),Map.of("lifeInsuranceRate","-0.01"),Map.of("propertyInsuranceRate","1.1"),
            Map.of("discountRateAnnual","-1"),Map.of("startDate","1899-12-31"),Map.of("startDate","9990-01-01","termMonths",360));
        for(var change:changes) {var d=data();d.putAll(change);calculate(d).andExpect(status().isBadRequest());}
        assertThat(quotations.count()).isZero();
        var accepted=data();accepted.put("startDate","9990-01-01");accepted.put("termMonths",108);
        calculate(accepted).andExpect(status().isCreated()).andExpect(jsonPath("schedule[107].dueDate").value("9999-01-01"));
    }
    @Test void preservesIndependentMonthEndDatesAndPositiveRegularEstimateAfterGrace()throws Exception {
        var d=data();d.put("termMonths",3);
        calculate(d).andExpect(status().isCreated()).andExpect(jsonPath("schedule[0].dueDate").value("2024-02-29"))
            .andExpect(jsonPath("schedule[1].dueDate").value("2024-03-31")).andExpect(jsonPath("schedule[2].dueDate").value("2024-04-30"));
        d=data();d.put("gracePeriodType","TOTAL");d.put("gracePeriodMonths",2);
        var result=response(calculate(d).andExpect(status().isCreated()).andReturn());
        assertThat(new BigDecimal(result.get("schedule").get(0).get("totalPayment").asText())).isEqualByComparingTo("0");
        assertThat(new BigDecimal(result.get("estimatedMonthlyInstallment").asText())).isPositive();
    }
    @Test void persistsMaximumMoneyAtMinimumRateAndFinalBalanceExactlyZero()throws Exception {
        var d=data();d.put("loanAmount","9999999999999999.99");d.put("propertyValue","9999999999999999.99");
        d.put("annualInterestRate","0.00000001");d.put("termMonths",360);
        var result=response(calculate(d).andExpect(status().isCreated()).andReturn());String id=result.get("quotationId").asText();
        assertThat(result.get("loanAmount").asText()).isEqualTo("9999999999999999.99");
        assertThat(result.get("schedule").get(359).get("finalBalance").asText()).isEqualTo("0.00");
        assertThat(new BigDecimal(result.get("metrics").get("totalInterest").asText())).isEqualByComparingTo("1504166734.80");
        var read=response(mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk()).andReturn());
        assertThat(read.get("metrics")).isEqualTo(result.get("metrics"));assertThat(read.get("schedule")).isEqualTo(result.get("schedule"));
        assertThat(result.toString()).doesNotContain("E-").doesNotContain("E+");
    }
    @Test void persistsHugeTceaAndNegativeVanForAdmittedExtremeFees()throws Exception {
        var d=data();d.put("loanAmount","0.01");d.put("propertyValue","9999999999999999.99");d.put("termMonths",1);
        d.put("annualInterestRate","0.00000001");d.put("lifeInsuranceRate","1");d.put("propertyInsuranceRate","1");
        d.put("monthlyAdminFee","9999999999999999.99");d.put("discountRateAnnual","0");
        var result=response(calculate(d).andExpect(status().isCreated()).andReturn());String id=result.get("quotationId").asText();
        BigDecimal tcea=new BigDecimal(result.get("metrics").get("tcea").asText());assertThat(tcea.precision()-tcea.scale()).isGreaterThan(200);
        assertThat(new BigDecimal(result.get("metrics").get("van").asText())).isEqualByComparingTo("-19999999999999999.99");
        var read=response(mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk()).andReturn());
        assertThat(read.get("metrics")).isEqualTo(result.get("metrics"));assertThat(result.toString()).doesNotContain("E+");
    }
    @Test void offerScheduleUsesFixedSnapshotAndOnlyRecipientCanGenerateOrReadIt()throws Exception {
        UUID offerId=offer();String path="/api/v1/offers/"+offerId+"/schedule";
        mvc.perform(get(path).header("Authorization","Bearer "+recipient.token())).andExpect(status().isNotFound());
        var snapshot=offers.findById(offerId).orElseThrow();
        mvc.perform(delete("/api/v1/properties/"+snapshot.propertyId).header("Authorization","Bearer "+advisor.token())).andExpect(status().isNoContent());
        var first=response(mvc.perform(post(path).header("Authorization","Bearer "+recipient.token()).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("loanAmount","1","annualInterestRate","1","termMonths",1))))
            .andExpect(status().isOk()).andReturn());
        var second=response(mvc.perform(post(path).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk()).andReturn());
        assertThat(second).isEqualTo(first);assertThat(first.get("loanAmount").asText()).isEqualTo("200000.00");
        assertThat(first.get("termMonths").asInt()).isEqualTo(12);assertThat(first.get("schedule").get(0).get("adminFee").asText()).isEqualTo("0.00");
        assertThat(quotations.count()).isEqualTo(1);
        var quote=quotationPort.findById(UUID.fromString(first.get("quotationId").asText())).orElseThrow();
        assertThat(quote.getPropertySnapshot().getPropertyId()).isEqualTo(snapshot.propertyId);assertThat(quote.getOwnerId()).isEqualTo(recipient.id());
        for(User denied:List.of(advisor,other)) {
            mvc.perform(get(path).header("Authorization","Bearer "+denied.token())).andExpect(status().isNotFound());
            mvc.perform(post(path).header("Authorization","Bearer "+denied.token())).andExpect(status().isNotFound());
        }
        mvc.perform(get(path).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk());
    }
    @Test void concurrentOfferGenerationCreatesOnlyOnePersistedSchedule()throws Exception {
        UUID offerId=offer();CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<UUID> generate=() -> {ready.countDown();go.await();return schedules.generate(recipient.id(),offerId).quotationId();};
            Future<UUID> first=executor.submit(generate),second=executor.submit(generate);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
        }
        assertThat(quotations.count()).isEqualTo(1);assertThat(offers.findById(offerId).orElseThrow().simulationId).isNotNull();
    }
    @Test void failedOfferLinkRollsBackTheSchedule()throws Exception {
        UUID offerId=offer();doThrow(new IllegalStateException("simulated association failure")).when(offerAdapter)
            .linkSimulation(eq(offerId),eq(recipient.id()),any(UUID.class));
        mvc.perform(post("/api/v1/offers/"+offerId+"/schedule").header("Authorization","Bearer "+recipient.token()))
            .andExpect(status().isInternalServerError());
        assertThat(quotations.count()).isZero();assertThat(offers.findById(offerId).orElseThrow().simulationId).isNull();
    }
}
