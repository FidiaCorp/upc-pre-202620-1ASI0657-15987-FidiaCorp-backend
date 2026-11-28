package com.fidiacorp.credicasa.catalog.interfaces.rest;
import com.fidiacorp.credicasa.shared.money.Currency;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.catalog.application.dto.request.CreatePropertyRequest;
import com.fidiacorp.credicasa.catalog.application.dto.request.VerifyRegistryRequest;
import com.fidiacorp.credicasa.catalog.testing.StubAccounts;
import com.fidiacorp.credicasa.catalog.infrastructure.persistence.repository.PropertyJpaRepository;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired StubAccounts accounts;
    @Autowired PropertyJpaRepository properties;

    private String token;
    private String otherToken;
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

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("firstName", "Otro", "lastName", "Usuario", "email", "intruder.sunarp@fidiacorp.com", "password", PASSWORD))))
                .andExpect(status().isCreated());
        otherToken = response(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", "intruder.sunarp@fidiacorp.com", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn()).get("token").asText();

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
    @DisplayName("US09: Validacion registral exitosa con partida valida, certificado SHA-256 real y badge en el catalogo")
    void testVerifyValidSunarpPartida() throws Exception {
        VerifyRegistryRequest req = new VerifyRegistryRequest("12345678", "Diego Antonio Ramos Hinostroza");

        MvcResult verified = mvc.perform(post("/api/v1/properties/" + propertyId + "/verify-registry")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_GENUINE"))
                .andExpect(jsonPath("$.legitimacyStatus").value("VERIFIED_GENUINE"))
                .andExpect(jsonPath("$.genuine").value(true))
                .andExpect(jsonPath("$.registryNumber").value("12345678"))
                .andExpect(jsonPath("$.legalOwnerName").value("Diego Antonio Ramos Hinostroza"))
                .andExpect(jsonPath("$.registryOffice").value("Zona Registral N° IX - Sede Lima"))
                .andReturn();
        JsonNode report = response(verified);
        String hash = report.get("certificateHash").asText();
        assertThat(hash).matches("[0-9a-f]{64}");
        Instant verifiedAt = Instant.parse(report.get("verifiedAt").asText());
        String payload = propertyId + "|12345678|Diego Antonio Ramos Hinostroza|VERIFIED_GENUINE|" + verifiedAt;
        assertThat(hash).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))));

        mvc.perform(get("/api/v1/properties/" + propertyId + "/legitimacy").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VERIFIED_GENUINE"))
                .andExpect(jsonPath("$.certificateHash").value(hash))
                .andExpect(jsonPath("$.legalOwnerName").value("Diego Antonio Ramos Hinostroza"))
                .andExpect(jsonPath("$.genuine").value(true));

        mvc.perform(get("/api/v1/properties/" + propertyId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legitimacyStatus").value("VERIFIED_GENUINE"))
                .andExpect(jsonPath("$.certificateHash").value(hash))
                .andExpect(jsonPath("$.legitimacyVerifiedAt").value(report.get("verifiedAt").asText()))
                .andExpect(jsonPath("$.legitimacyNotes").isNotEmpty());
    }

    @Test
    @DisplayName("US09: Partida con formato invalido o con carga no saneada genera FLAGGED_ANOMALY")
    void testVerifyInvalidSunarpPartida() throws Exception {
        for (String partida : new String[]{"XYZ-99", "12349999"}) {
            mvc.perform(post("/api/v1/properties/" + propertyId + "/verify-registry")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new VerifyRegistryRequest(partida, null))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FLAGGED_ANOMALY"))
                    .andExpect(jsonPath("$.genuine").value(false))
                    .andExpect(jsonPath("$.certificateHash").isNotEmpty());
        }
    }

    @Test
    @DisplayName("US09 (OWASP API1 BOLA/IDOR): un tercero no puede alterar la legitimidad registral de un inmueble ajeno")
    void nonOwnerCannotVerifyRegistry() throws Exception {
        mvc.perform(post("/api/v1/properties/" + propertyId + "/verify-registry")
                .header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new VerifyRegistryRequest("99999999", "Atacante"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"));

        mvc.perform(get("/api/v1/properties/" + propertyId + "/legitimacy").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.certificateHash").doesNotExist());
        mvc.perform(get("/api/v1/properties/" + UUID.randomUUID() + "/legitimacy").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/properties/" + propertyId + "/verify-registry")
                .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"registryNumber\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.validationErrors.registryNumber").exists());
    }
}
