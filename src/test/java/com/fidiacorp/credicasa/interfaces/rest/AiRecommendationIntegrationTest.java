package com.fidiacorp.credicasa.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.application.dto.request.AiRecommendationRequest;
import com.fidiacorp.credicasa.application.dto.request.CreatePropertyRequest;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.PropertyJpaRepository;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ai;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.jwt.secret=test-signing-key-minimum-length-is-thirty-two-bytes"
})
@AutoConfigureMockMvc
class AiRecommendationIntegrationTest {

    private static final String PASSWORD = "CorrectHorseBattery123!";
    private static final String EMAIL = "ai.user@fidiacorp.com";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired PropertyJpaRepository properties;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        properties.deleteAll();
        accounts.deleteAll();

        // Register user
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "firstName", "Sebastian",
                        "lastName", "Rubio",
                        "email", EMAIL,
                        "password", PASSWORD
                ))))
                .andExpect(status().isCreated());

        // Login
        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "email", EMAIL,
                        "password", PASSWORD
                ))))
                .andExpect(status().isOk())
                .andReturn();

        token = json.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();

        // Seed 2 properties: 1 matching district + tags, 1 expensive
        CreatePropertyRequest p1 = new CreatePropertyRequest(
                "Depa Ideal en Miraflores con Terraza",
                "Calle Berlín 450, Dpto 302, Miraflores, Lima",
                "Departamento moderno con terraza amplia y vista despejada.",
                new BigDecimal("280000.00"),
                Currency.PEN,
                List.of("miraflores", "terraza", "estreno")
        );
        CreatePropertyRequest p2 = new CreatePropertyRequest(
                "Mansión Exclusiva en La Molina",
                "Av. El Sol 999, La Molina, Lima",
                "Mansión de lujo con piscina y 6 habitaciones.",
                new BigDecimal("3500000.00"),
                Currency.PEN,
                List.of("piscina", "lujo", "jardin")
        );

        mvc.perform(post("/api/v1/properties").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p1)))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/properties").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(p2)))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("US15: Motor heuristico determinista de IA evalua capacidad y recomienda inmuebles idoneos")
    void testAiMortgageRecommendations() throws Exception {
        AiRecommendationRequest req = new AiRecommendationRequest(
                new BigDecimal("6000.00"), // Ingreso mensual
                new BigDecimal("50000.00"), // Cuota inicial
                Currency.PEN,
                "Miraflores",
                20,
                List.of("terraza", "estreno")
        );

        mvc.perform(post("/api/v1/ai/recommendations")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(6000.00))
                .andExpect(jsonPath("$.maxAllowableInstallment").value(2100.00)) // 35% de 6000
                .andExpect(jsonPath("$.totalAnalyzed").value(2))
                .andExpect(jsonPath("$.recommendations", hasSize(2)))
                // Primer resultado debe ser Miraflores (score alto)
                .andExpect(jsonPath("$.recommendations[0].title").value("Depa Ideal en Miraflores con Terraza"))
                .andExpect(jsonPath("$.recommendations[0].financiallyViable").value(true))
                .andExpect(jsonPath("$.recommendations[0].relevanceScore", greaterThan(70)))
                // Segundo resultado es Mansión (score bajo, no viable por sobreendeudamiento)
                .andExpect(jsonPath("$.recommendations[1].title").value("Mansión Exclusiva en La Molina"))
                .andExpect(jsonPath("$.recommendations[1].financiallyViable").value(false));
    }
}
