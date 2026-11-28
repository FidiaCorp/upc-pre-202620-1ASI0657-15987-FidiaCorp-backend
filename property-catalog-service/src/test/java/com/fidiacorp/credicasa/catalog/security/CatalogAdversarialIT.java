package com.fidiacorp.credicasa.catalog.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QA-03 en catálogo: V05/V06 (BOLA en US09/US08), tokens forjados/expirados y roles sin privilegio. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalog_adversarial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CatalogAdversarialIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.security.jwt.secret}") String secret;

    private String token(UUID id, String role) {
        return new JwtTokenIssuer(secret, 60_000).issue(id, List.of(role), id + "@example.com", "Actor");
    }

    @Test
    @DisplayName("QA-03 catálogo: BOLA sobre verificación registral y borrado ajeno, JWT forjado/expirado y rol inválido")
    void adversarialVectorsAreRejected() throws Exception {
        String victim = token(UUID.randomUUID(), "ROLE_CLIENT");
        String attacker = token(UUID.randomUUID(), "ROLE_REALTOR");
        String id = json.readTree(mvc.perform(post("/api/v1/properties").header("Authorization", "Bearer " + victim)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("title", "Casa privada víctima",
                        "address", "Miraflores", "price", "300000.00", "currency", "PEN")))).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(post("/api/v1/properties/" + id + "/verify-registry").header("Authorization", "Bearer " + attacker)
                .contentType(MediaType.APPLICATION_JSON).content("{\"registryNumber\":\"12345678\",\"claimedOwner\":\"Atacante\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/properties/" + id).header("Authorization", "Bearer " + attacker)).andExpect(status().isForbidden());
        String foreignKey = new JwtTokenIssuer("another-secret-signing-key-of-at-least-32-bytes!!", 60_000)
                .issue(UUID.randomUUID(), List.of("ROLE_REALTOR"), null, null);
        mvc.perform(get("/api/v1/properties").header("Authorization", "Bearer " + foreignKey)).andExpect(status().isUnauthorized());
        String expired = new JwtTokenIssuer(secret, 1_000, Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC))
                .issue(UUID.randomUUID(), List.of("ROLE_CLIENT"), null, null);
        mvc.perform(get("/api/v1/properties").header("Authorization", "Bearer " + expired)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/properties").header("Authorization", "Bearer " + token(UUID.randomUUID(), "ROLE_VIEWER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/properties/" + id + "/legitimacy").header("Authorization", "Bearer " + victim))
                .andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"));
        mvc.perform(get("/api/v1/properties/" + id).header("Authorization", "Bearer " + victim)).andExpect(status().isOk());
    }
}
