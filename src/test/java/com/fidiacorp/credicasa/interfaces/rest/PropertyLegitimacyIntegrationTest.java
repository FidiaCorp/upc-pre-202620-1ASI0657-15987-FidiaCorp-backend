package com.fidiacorp.credicasa.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.application.dto.request.CreatePropertyRequest;
import com.fidiacorp.credicasa.application.dto.request.VerifyRegistryRequest;
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
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:sunarp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.jwt.secret=test-signing-key-minimum-length-is-thirty-two-bytes"
})
@AutoConfigureMockMvc
class PropertyLegitimacyIntegrationTest {

    private static final String PASSWORD = "CorrectHorseBattery123!";
    private static final String EMAIL = "realtor.sunarp@fidiacorp.com";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired PropertyJpaRepository properties;

    private String token;
    private UUID propertyId;

    private JsonNode response(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @BeforeEach
    void setUp() throws Exception {
        properties.deleteAll();
        accounts.deleteAll();

        // Register realtor
        mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "firstName", "Diego",
                        "lastName", "Ramos",
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

        // Create a property
        CreatePropertyRequest createReq = new CreatePropertyRequest(
                "Departamento Residencial Miraflores",
                "Av. Larco 1234, Dpto 501, Miraflores, Lima",
                "Hermoso departamento frente a parque con acabados de primera.",
                new BigDecimal("450000.00"),
                Currency.PEN,
                List.of("miraflores", "vista-parque", "estreno")
        );

        MvcResult createResult = mvc.perform(post("/api/v1/properties")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn();

        propertyId = UUID.fromString(response(createResult).get("id").asText());
    }

    @Test
    @DisplayName("US09: Validacion registral exitosa con partida Sunarp valida de 8 digitos")
    void testVerifyValidSunarpPartida() throws Exception {
        VerifyRegistryRequest req = new VerifyRegistryRequest("12345678", "Diego Antonio Ramos Hinostroza");

        mvc.perform(post("/api/v1/properties/" + propertyId + "/verify-registry")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_GENUINE"))
                .andExpect(jsonPath("$.genuine").value(true))
                .andExpect(jsonPath("$.registryNumber").value("12345678"))
                .andExpect(jsonPath("$.registryOffice").value("Zona Registral N° IX - Sede Lima"));

        // Comprobar persistencia via GET /legitimacy
        mvc.perform(get("/api/v1/properties/" + propertyId + "/legitimacy")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_GENUINE"))
                .andExpect(jsonPath("$.genuine").value(true));
    }

    @Test
    @DisplayName("US09: Partida con formato invalido genera estado FLAGGED_ANOMALY")
    void testVerifyInvalidSunarpPartida() throws Exception {
        VerifyRegistryRequest req = new VerifyRegistryRequest("XYZ-99", "Titular No Verificado");

        mvc.perform(post("/api/v1/properties/" + propertyId + "/verify-registry")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FLAGGED_ANOMALY"))
                .andExpect(jsonPath("$.genuine").value(false));
    }
}
