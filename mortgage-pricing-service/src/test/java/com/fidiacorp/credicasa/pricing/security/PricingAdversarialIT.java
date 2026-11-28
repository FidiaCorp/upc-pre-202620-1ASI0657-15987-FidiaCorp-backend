package com.fidiacorp.credicasa.pricing.security;

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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QA-03 en pricing: V07/V08 (BOLA sobre simulaciones y exportaciones ajenas) y sin token. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pricing_adversarial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PricingAdversarialIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.security.jwt.secret}") String secret;

    private String token(String role) {
        UUID id = UUID.randomUUID();
        return new JwtTokenIssuer(secret, 60_000).issue(id, List.of(role), id + "@example.com", "Víctima Privada");
    }

    @Test
    @DisplayName("QA-03 pricing: simulaciones ajenas, exportación ajena, listado y comparación sin token son rechazados")
    void adversarialVectorsAreRejected() throws Exception {
        String victim = token("ROLE_CLIENT");
        String attacker = token("ROLE_CLIENT");
        String id = json.readTree(mvc.perform(post("/api/v1/simulations/calculate").header("Authorization", "Bearer " + victim)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("loanAmount", "100000.00", "propertyValue", "150000.00",
                        "currency", "PEN", "annualInterestRate", "0.09", "termMonths", 60)))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("quotationId").asText();
        mvc.perform(get("/api/v1/simulations/" + id).header("Authorization", "Bearer " + attacker)).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Víctima"))));
        mvc.perform(get("/api/v1/simulations/" + id + "/export/xlsx").header("Authorization", "Bearer " + attacker)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/simulations/compare").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/simulations").header("Authorization", "Bearer " + token("ROLE_VIEWER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/simulations").header("Authorization", "Bearer " + attacker)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(id))));
        mvc.perform(get("/api/v1/banking/benchmarks")).andExpect(status().isOk());
    }
}
