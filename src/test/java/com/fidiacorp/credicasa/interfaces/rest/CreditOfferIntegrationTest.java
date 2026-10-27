package com.fidiacorp.credicasa.interfaces.rest;
import com.fasterxml.jackson.databind.*;
import com.fidiacorp.credicasa.application.usecase.OfferNotificationService;
import com.fidiacorp.credicasa.domain.model.OfferNotification;
import com.fidiacorp.credicasa.infrastructure.persistence.OfferNotificationRepositoryAdapter;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:creditoffers;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class CreditOfferIntegrationTest {
    private static final String PASSWORD="CorrectHorseBattery123!",BASE="/api/v1/offers",NOTICES="/api/v1/notifications";
    record User(UUID id,String token,String email) {}
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired PropertyJpaRepository properties;
    @Autowired CreditOfferJpaRepository offers;
    @Autowired OfferNotificationJpaRepository notices;
    @Autowired OfferNotificationService notificationService;
    @SpyBean OfferNotificationRepositoryAdapter notificationAdapter;
    private User issuer,recipient,other;
    private UUID propertyId;
    private String body(Object o)throws Exception{return json.writeValueAsString(o);}
    private JsonNode response(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString());}
    private User register(String email)throws Exception {
        var r=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Lucía","lastName","Pérez","email",email,"password",PASSWORD))))
            .andExpect(status().isCreated()).andReturn();
        var l=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email",email,"password",PASSWORD)))).andExpect(status().isOk()).andReturn();
        return new User(UUID.fromString(response(r).get("id").asText()),response(l).get("token").asText(),email);
    }
    private UUID property(User owner,String price)throws Exception {
        return UUID.fromString(response(mvc.perform(post("/api/v1/properties").header("Authorization","Bearer "+owner.token())
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("title","Casa demostrativa","address","Av. Central 120, Lima",
                "price",price,"currency","PEN","tags",List.of("terraza"))))).andExpect(status().isCreated()).andReturn()).get("id").asText());
    }
    @BeforeEach void prepare()throws Exception {
        notices.deleteAll();offers.deleteAll();properties.deleteAll();accounts.deleteAll();
        issuer=register("advisor@example.com");recipient=register("client@example.com");other=register("other@example.com");
        var advisor=accounts.findById(issuer.id()).orElseThrow();advisor.roles="ROLE_REALTOR";accounts.saveAndFlush(advisor);
        propertyId=property(issuer,"250000.00");
    }
    private Map<String,Object> data() {
        Map<String,Object> d=new HashMap<>();d.put("recipientEmail"," CLIENT@EXAMPLE.COM ");d.put("propertyId",propertyId);
        d.put("bankCode","BCP");d.put("loanAmount","200000.00");d.put("currency","PEN");
        d.put("annualInterestRate","0.08512345");d.put("termMonths",240);d.put("startDate","2027-01-01");return d;
    }
    private ResultActions issue(User caller,Map<String,Object> d)throws Exception {
        return mvc.perform(post(BASE).header("Authorization","Bearer "+caller.token()).contentType(MediaType.APPLICATION_JSON).content(body(d)));
    }
    private JsonNode issue()throws Exception{return response(issue(issuer,data()).andExpect(status().isCreated()).andReturn());}
    @Test void persistsDemoOfferAndNotificationAtomicallyWithDerivedIdentityAndBank()throws Exception {
        var d=data();d.put("issuerId",other.id());d.put("recipientId",other.id());d.put("origin","REAL");d.put("title","Bank approved");
        var offer=response(issue(issuer,d).andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("origin").value("DEMO")).andExpect(jsonPath("issuerId").value(issuer.id().toString()))
            .andExpect(jsonPath("recipientId").value(recipient.id().toString())).andExpect(jsonPath("bankCode").value("BCP"))
            .andExpect(jsonPath("title").value("Oferta demostrativa de Banco de Crédito del Perú"))
            .andExpect(jsonPath("loanAmount").value("200000.00")).andExpect(jsonPath("annualInterestRate").value("0.08512345"))
            .andExpect(jsonPath("simulationId").isEmpty()).andReturn());
        assertThat(offers.count()).isEqualTo(1);assertThat(notices.count()).isEqualTo(1);
        mvc.perform(get(NOTICES).header("Authorization","Bearer "+recipient.token()))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("unreadCount").value(1))
            .andExpect(jsonPath("items[0].offerId").value(offer.get("id").asText()))
            .andExpect(jsonPath("items[0].origin").value("DEMO")).andExpect(jsonPath("items[0].type").value("CREDIT_OFFER"));
    }
    @Test void failedNotificationInsertRollsBackOfferInsteadOfLeavingPartialData()throws Exception {
        doThrow(new IllegalStateException("simulated insert failure")).when(notificationAdapter).save(any(OfferNotification.class));
        issue(issuer,data()).andExpect(status().isInternalServerError());
        assertThat(offers.count()).isZero();assertThat(notices.count()).isZero();
    }
    @Test void receivedListsDetailsAndNotificationsBelongOnlyToRecipient()throws Exception {
        String id=issue().get("id").asText();
        mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk());
        for(User denied:List.of(issuer,other)) {
            mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+denied.token())).andExpect(status().isNotFound());
            mvc.perform(get(BASE).header("Authorization","Bearer "+denied.token())).andExpect(jsonPath("items").isEmpty());
            mvc.perform(get(NOTICES).header("Authorization","Bearer "+denied.token()))
                .andExpect(jsonPath("items").isEmpty()).andExpect(jsonPath("unreadCount").value(0));
        }
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(NOTICES)).andExpect(status().isUnauthorized());
    }
    @Test void onlyRealtorOwnActivePropertyAndClientRecipientCanReceiveDemoOffer()throws Exception {
        issue(recipient,data()).andExpect(status().isForbidden());
        var d=data();d.put("propertyId",property(other,"300000.00"));issue(issuer,d).andExpect(status().isForbidden());
        d=data();d.put("recipientEmail",issuer.email());issue(issuer,d).andExpect(status().isNotFound());
        d=data();d.put("recipientEmail","missing@example.com");issue(issuer,d).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/properties/"+propertyId).header("Authorization","Bearer "+issuer.token())).andExpect(status().isNoContent());
        issue(issuer,data()).andExpect(status().isNotFound());
        assertThat(offers.count()).isZero();assertThat(notices.count()).isZero();
    }
    @Test void validatesMoneyRateTermsCurrencyAndPageBounds()throws Exception {
        for(var change:List.of(Map.entry("loanAmount",(Object)"0"),Map.entry("loanAmount",(Object)"250000.01"),
            Map.entry("loanAmount",(Object)"1.001"),Map.entry("annualInterestRate",(Object)"0"),Map.entry("annualInterestRate",(Object)"8.5"),
            Map.entry("annualInterestRate",(Object)"0.123456789"),Map.entry("termMonths",(Object)0),Map.entry("termMonths",(Object)361),
            Map.entry("currency",(Object)"USD"),Map.entry("bankCode",(Object)"UNKNOWN"),Map.entry("startDate",(Object)"invalid"))) {
            var d=data();d.put(change.getKey(),change.getValue());issue(issuer,d).andExpect(status().isBadRequest());
        }
        assertThat(offers.count()).isZero();assertThat(notices.count()).isZero();
        for(String route:List.of(BASE,NOTICES))for(var change:List.of(Map.entry("page","-1"),Map.entry("size","0"),Map.entry("size","101"))) {
            mvc.perform(get(route).header("Authorization","Bearer "+recipient.token()).param(change.getKey(),change.getValue()))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get(BASE+"/invalid").header("Authorization","Bearer "+recipient.token())).andExpect(status().isBadRequest());
    }
    @Test void retainsExactMaximumAmountsAndSnapshotAfterCatalogueDeletion()throws Exception {
        propertyId=property(issuer,"9999999999999999.99");var d=data();d.put("loanAmount","9999999999999999.99");d.put("bankCode","INTERBANK");
        var offer=response(issue(issuer,d).andExpect(status().isCreated())
            .andExpect(jsonPath("loanAmount").value("9999999999999999.99"))
            .andExpect(jsonPath("property.price").value("9999999999999999.99")).andReturn());
        String id=offer.get("id").asText();
        JsonNode before=response(mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk()).andReturn());
        mvc.perform(delete("/api/v1/properties/"+propertyId).header("Authorization","Bearer "+issuer.token())).andExpect(status().isNoContent());
        JsonNode after=response(mvc.perform(get(BASE+"/"+id).header("Authorization","Bearer "+recipient.token())).andExpect(status().isOk()).andReturn());
        assertThat(after).isEqualTo(before);assertThat(after.get("bankName").asText()).isEqualTo("Interbank");
    }
    @Test void marksOnlyOwnNotificationReadIdempotentlyAndCountsUnreadAcrossPages()throws Exception {
        issue();issue();String id=notices.findAll().getFirst().id.toString();
        mvc.perform(patch(NOTICES+"/"+id+"/read").header("Authorization","Bearer "+other.token())).andExpect(status().isNotFound());
        assertThat(notices.findById(UUID.fromString(id)).orElseThrow().readAt).isNull();
        var first=response(mvc.perform(patch(NOTICES+"/"+id+"/read").header("Authorization","Bearer "+recipient.token()))
            .andExpect(status().isOk()).andReturn());
        var second=response(mvc.perform(patch(NOTICES+"/"+id+"/read").header("Authorization","Bearer "+recipient.token()))
            .andExpect(status().isOk()).andReturn());assertThat(second).isEqualTo(first);
        var persisted=notices.findById(UUID.fromString(id)).orElseThrow();persisted.readAt=Instant.parse("2020-01-01T00:00:00Z");notices.saveAndFlush(persisted);
        mvc.perform(patch(NOTICES+"/"+id+"/read").header("Authorization","Bearer "+recipient.token()))
            .andExpect(jsonPath("readAt").value("2020-01-01T00:00:00Z"));
        mvc.perform(get(NOTICES).header("Authorization","Bearer "+recipient.token()).param("unreadOnly","true").param("size","1"))
            .andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("unreadCount").value(1)).andExpect(jsonPath("items.length()").value(1));
        mvc.perform(get(NOTICES).header("Authorization","Bearer "+recipient.token()).param("size","1").param("page","1"))
            .andExpect(jsonPath("totalElements").value(2)).andExpect(jsonPath("unreadCount").value(1));
    }
    @Test void paginatesReceivedOffersWithoutDuplicatesAndConcurrentReadPreservesTimestamp()throws Exception {
        issue();issue();issue();Set<String> ids=new HashSet<>();
        for(int page=0;page<3;page++) {
            var node=response(mvc.perform(get(BASE).header("Authorization","Bearer "+recipient.token()).param("size","1").param("page",Integer.toString(page)))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(3)).andExpect(jsonPath("totalPages").value(3)).andReturn());
            ids.add(node.get("items").get(0).get("id").asText());
        }
        assertThat(ids).hasSize(3);UUID notice=notices.findAll().getFirst().id;
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<Instant> mark=() -> {ready.countDown();go.await();return notificationService.markRead(recipient.id(),notice).readAt();};
            Future<Instant> first=executor.submit(mark),second=executor.submit(mark);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)).isEqualTo(second.get(10,TimeUnit.SECONDS));
        }
    }
}
