package com.fidiacorp.credicasa.identity.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.identity.application.dto.request.VerifyPaymentRequest;
import com.fidiacorp.credicasa.identity.infrastructure.persistence.repository.AccountJpaRepository;
import com.fidiacorp.credicasa.identity.infrastructure.persistence.repository.PaymentRecordJpaRepository;
import com.fidiacorp.credicasa.identity.infrastructure.persistence.repository.SubscriptionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:subscriptions;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.jwt.secret=test-signing-key-minimum-length-is-thirty-two-bytes"
})
@AutoConfigureMockMvc
class SubscriptionIntegrationTest {

    private static final String PASSWORD = "CorrectHorseBattery123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired SubscriptionJpaRepository subscriptions;
    @Autowired PaymentRecordJpaRepository payments;

    private String token;
    private String otherToken;

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("firstName", "Oliver", "lastName", "Jonseck", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        return body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD)))).andExpect(status().isOk()).andReturn())
                .get("token").asText();
    }

    private ResultActions postJson(String path, String bearer, Object payload) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(payload)));
    }

    private JsonNode checkout(String bearer, String gateway, String plan, String cardToken, int expectedStatus) throws Exception {
        return body(postJson("/api/v1/payments/checkout", bearer, Map.of("gatewayCode", gateway, "planName", plan, "cardToken", cardToken))
                .andExpect(status().is(expectedStatus)).andReturn());
    }

    @BeforeEach
    void setUp() throws Exception {
        payments.deleteAll();
        subscriptions.deleteAll();
        accounts.deleteAll();
        token = register("client.subscriptions@fidiacorp.com");
        otherToken = register("other.subscriptions@fidiacorp.com");
    }

    @Test
    @DisplayName("US06: Consulta inicial sin suscripcion retorna estado PENDING / FREE_TIER con 0 pagos")
    void testGetSubscriptionInitialPending() throws Exception {
        mvc.perform(get("/api/v1/subscriptions/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.planName").value("FREE_TIER"))
                .andExpect(jsonPath("$.paymentCount").value(0));
    }

    @Test
    @DisplayName("US05: lista las 3 pasarelas sandbox con tarifas oficiales por plan")
    void listsSandboxGateways() throws Exception {
        mvc.perform(get("/api/v1/payments/gateways").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].code").value("CULQI"))
                .andExpect(jsonPath("$[1].code").value("IZIPAY"))
                .andExpect(jsonPath("$[2].code").value("STRIPE"))
                .andExpect(jsonPath("$[0].sandbox").value(true))
                .andExpect(jsonPath("$[0].planFees.BASIC").value("0.00"))
                .andExpect(jsonPath("$[0].planFees.PREMIUM").value("49.90"))
                .andExpect(jsonPath("$[0].planFees.PRO").value("99.90"));
    }

    @Test
    @DisplayName("US05+US06: rechazo por fondos insuficientes (402), reintento con otro procesador y activación del plan")
    void declinedThenRetryWithAnotherGatewayActivatesSubscription() throws Exception {
        JsonNode rejected = checkout(token, "IZIPAY", "PREMIUM", "tok_DECLINED_INSUFFICIENT_FUNDS", 402);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("declineCode").asText()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(rejected.get("retryAllowed").asBoolean()).isTrue();
        assertThat(rejected.get("transactionReference").isNull()).isTrue();
        assertThat(checkout(token, "STRIPE", "PREMIUM", "tok_DECLINED_generic", 402).get("declineCode").asText()).isEqualTo("CARD_DECLINED");

        JsonNode approved = checkout(token, "CULQI", "PREMIUM", "tok_visa_approved", 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        assertThat(approved.get("amount").asText()).isEqualTo("49.90");
        assertThat(approved.get("commission").asText()).isEqualTo("1.72");
        String reference = approved.get("transactionReference").asText();
        assertThat(reference).matches("CULQI-[0-9a-f]{32}-[0-9a-f]{40}");

        VerifyPaymentRequest request = new VerifyPaymentRequest(reference, "CULQI", new BigDecimal("49.90"), "PEN", "PREMIUM");
        postJson("/api/v1/payments/verify", token, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.planName").value("PREMIUM"))
                .andExpect(jsonPath("$.amount").value("49.90"))
                .andExpect(jsonPath("$.transactionReference").value(reference));

        // Idempotent for the same owner.
        postJson("/api/v1/payments/verify", token, request).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Transacción previamente verificada y procesada."));

        mvc.perform(get("/api/v1/subscriptions/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.planName").value("PREMIUM"))
                .andExpect(jsonPath("$.paymentCount").value(1))
                .andExpect(jsonPath("$.endDate").isNotEmpty());

        // OWASP API1: another account cannot read or reuse the receipt.
        postJson("/api/v1/payments/verify", otherToken, request)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.transactionReference").doesNotExist())
                .andExpect(jsonPath("$.paymentId").doesNotExist());
    }

    @Test
    @DisplayName("US06 (anti auto-activación): monto 0.01, plan/moneda/pasarela incoherentes o referencia forjada son rechazados")
    void forgedOrUnderpaidPaymentsNeverActivatePro() throws Exception {
        String reference = checkout(token, "STRIPE", "PRO", "tok_mastercard_ok", 200).get("transactionReference").asText();
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "STRIPE", new BigDecimal("0.01"), "PEN", "PRO"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "STRIPE", new BigDecimal("99.90"), "USD", "PRO"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "CULQI", new BigDecimal("99.90"), "PEN", "PRO"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "STRIPE", new BigDecimal("49.90"), "PEN", "PREMIUM"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest("TX-ANY-STRING", "STRIPE", new BigDecimal("99.90"), "PEN", "PRO"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/v1/payments/verify", otherToken, new VerifyPaymentRequest(reference, "STRIPE", new BigDecimal("99.90"), "PEN", "PRO"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "STRIPE", new BigDecimal("0.00"), "PEN", "PRO"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.amount").exists());
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "STRIPE", new BigDecimal("99.90"), "PEN", "GOLD"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "STRIPE", new BigDecimal("0.01"), "PEN", "BASIC"))
                .andExpect(status().isBadRequest());
        assertThat(subscriptions.count()).isZero();
        assertThat(payments.count()).isZero();

        postJson("/api/v1/payments/verify", token, new VerifyPaymentRequest(reference, "stripe", new BigDecimal("99.9"), "pen", "pro"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.planName").value("PRO"));
    }

    @Test
    @DisplayName("US05: validaciones de checkout (pasarela, plan gratuito, moneda, token)")
    void checkoutValidation() throws Exception {
        postJson("/api/v1/payments/checkout", token, Map.of("gatewayCode", "PAYPAL", "planName", "PRO", "cardToken", "tok_visa_ok"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.gatewayCode").exists());
        postJson("/api/v1/payments/checkout", token, Map.of("gatewayCode", "CULQI", "planName", "BASIC", "cardToken", "tok_visa_ok"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/payments/checkout", token, Map.of("gatewayCode", "CULQI", "planName", "PRO", "cardToken", "tok_visa_ok", "currency", "USD"))
                .andExpect(status().isBadRequest());
        postJson("/api/v1/payments/checkout", token, Map.of("gatewayCode", "CULQI", "planName", "PRO", "cardToken", "x"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.cardToken").exists());
        mvc.perform(get("/api/v1/payments/gateways")).andExpect(status().isUnauthorized());
    }
}
