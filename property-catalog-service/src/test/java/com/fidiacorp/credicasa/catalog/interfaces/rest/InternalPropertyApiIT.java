package com.fidiacorp.credicasa.catalog.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API interna del catálogo para quotation-deal y communication-notification (sin FK física a properties). */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:catalog_internal;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.internal.token=internal-secret"})
@AutoConfigureMockMvc
class InternalPropertyApiIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.security.jwt.secret}") String secret;

    @Test
    void exposesActivePropertySummaryOnlyToInternalCallers() throws Exception {
        UUID owner = UUID.randomUUID();
        String token = new JwtTokenIssuer(secret, 60_000).issue(owner, List.of("ROLE_REALTOR"), "r@example.com", "Realtor");
        String id = json.readTree(mvc.perform(post("/api/v1/properties").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("title", "Casa DEMO", "address", "San Borja",
                        "price", "250000.00", "currency", "PEN")))).andReturn().getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/v1/internal/properties/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/internal/properties/" + id).header("X-Internal-Token", "internal-secret")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.price").value("250000.00")).andExpect(jsonPath("$.currency").value("PEN"))
                .andExpect(jsonPath("$.title").value("Casa DEMO"));
        mvc.perform(delete("/api/v1/properties/" + id).header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/internal/properties/" + id).header("X-Internal-Token", "internal-secret")
                .header("Authorization", "Bearer " + token)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("https://api.credicasa.pe/problems/not-found"));
    }
}
