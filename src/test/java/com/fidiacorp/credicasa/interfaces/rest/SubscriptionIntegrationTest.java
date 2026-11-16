package com.fidiacorp.credicasa.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.application.dto.request.VerifyPaymentRequest;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.PaymentRecordJpaRepository;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.SubscriptionJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.Map;

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
    private static final String EMAIL = "client.subscriptions@fidiacorp.com";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired SubscriptionJpaRepository subscriptions;
    @Autowired PaymentRecordJpaRepository payments;

    private String token;

    private JsonNode response(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @BeforeEach
    void setUp() throws Exception {
        payments.deleteAll();
        subscriptions.deleteAll();
        accounts.deleteAll();

        // Register client
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "firstName", "Oliver",
                        "lastName", "Jonseck",
                        "email", EMAIL,
                        "password", PASSWORD
                ))))
                .andExpect(status().isCreated());

        // Login to get token
        MvcResult loginResult = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", EMAIL,
                        "password", PASSWORD
                ))))
                .andExpect(status().isOk())
                .andReturn();

        token = response(loginResult).get("token").asText();
    }

    @Test
    @DisplayName("US06: Consulta inicial sin suscripcion retorna estado PENDING / FREE_TIER")
    void testGetSubscriptionInitialPending() throws Exception {
        mvc.perform(get("/api/v1/subscriptions/status")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.planName").value("FREE_TIER"));
    }

    @Test
    @DisplayName("US06: Verificacion de pago exitoso activa suscripcion con fecha de vencimiento")
    void testVerifyPaymentAndActivateSubscription() throws Exception {
        VerifyPaymentRequest request = new VerifyPaymentRequest(
                "TX-CULQI-987654321",
                "CULQI_CHECKOUT",
                new BigDecimal("49.90"),
                "PEN",
                "PREMIUM_PROPTECH"
        );

        mvc.perform(post("/api/v1/payments/verify")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.subscriptionStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.transactionReference").value("TX-CULQI-987654321"));

        // Verificar que la consulta posterior de suscripcion refleje estado activo
        mvc.perform(get("/api/v1/subscriptions/status")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.planName").value("PREMIUM_PROPTECH"));
    }
}
