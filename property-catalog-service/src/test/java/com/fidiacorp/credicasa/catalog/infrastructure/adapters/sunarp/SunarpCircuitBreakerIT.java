package com.fidiacorp.credicasa.catalog.infrastructure.adapters.sunarp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US09 + QA-02: el circuito {@code sunarpRegistry} se abre ante fallos y la verificación degrada a PENDING sellado. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalog_cb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class SunarpCircuitBreakerIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CircuitBreakerRegistry registry;
    @SpyBean SunarpRegistryAdapter delegate;
    @Value("${app.security.jwt.secret}") String secret;

    @Test
    void repeatedRegistryFailuresOpenTheCircuitAndDegradeToPendingVerification() throws Exception {
        UUID owner = UUID.randomUUID();
        String token = new JwtTokenIssuer(secret, 60_000).issue(owner, List.of("ROLE_CLIENT"), "o@example.com", "Owner");
        String id = json.readTree(mvc.perform(post("/api/v1/properties").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("title", "Casa", "address", "Lima",
                        "price", "200000.00", "currency", "PEN")))).andReturn().getResponse().getContentAsString()).get("id").asText();
        CircuitBreaker breaker = registry.circuitBreaker("sunarpRegistry");
        breaker.reset();
        doThrow(new IllegalStateException("SUNARP timeout")).when(delegate).verifyRegistryNumber(any(), any(), any());
        JsonNode last = null;
        for (int i = 0; i < 6; i++) {
            last = json.readTree(mvc.perform(post("/api/v1/properties/" + id + "/verify-registry").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"registryNumber\":\"14298172\",\"claimedOwner\":\"Owner\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_VERIFICATION"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(last.get("verificationNotes").asText()).isEqualTo(ResilientSunarpRegistryAdapter.FALLBACK_NOTES);
        String payload = id + "|14298172|Owner|PENDING_VERIFICATION|" + last.get("verifiedAt").asText();
        assertThat(last.get("certificateHash").asText()).isEqualTo(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8))));
        mvc.perform(get("/actuator/circuitbreakers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakers.sunarpRegistry.state").value("OPEN"));

        reset(delegate);
        breaker.transitionToHalfOpenState();
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/properties/" + id + "/verify-registry").header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"registryNumber\":\"14298172\",\"claimedOwner\":\"Owner\"}"))
                    .andExpect(jsonPath("$.status").value("VERIFIED_GENUINE"));
        }
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
