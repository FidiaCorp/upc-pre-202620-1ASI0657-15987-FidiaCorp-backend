package com.fidiacorp.credicasa.identity.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API interna sin FK cruzadas: directorio de cuentas, introspección de tokens, X-Internal-Token y fixture E2E. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:internal_api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.internal.token=internal-secret"})
@AutoConfigureMockMvc
class InternalAccountApiIT {
    private static final String PASSWORD = "InternalApiPassword2026!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String[] register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String id = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("firstName", "Rosa", "lastName", "Quispe", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        String token = json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("token").asText();
        return new String[]{id, email, token};
    }

    @Test
    void directoryRequiresInternalTokenAndJwtAndNeverLeaksPasswordHashes() throws Exception {
        String[] user = register("dir");
        mvc.perform(get("/api/v1/internal/accounts/" + user[0]).header("Authorization", "Bearer " + user[2]))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/internal/accounts/" + user[0]).header("X-Internal-Token", "internal-secret"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/internal/accounts/" + user[0]).header("X-Internal-Token", "internal-secret")
                        .header("Authorization", "Bearer " + user[2]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.displayName").value("Rosa Quispe"))
                .andExpect(jsonPath("$.email").value(user[1])).andExpect(jsonPath("$.roles[0]").value("ROLE_CLIENT"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
        mvc.perform(get("/api/v1/internal/accounts/" + user[0] + "/exists").header("X-Internal-Token", "internal-secret")
                .header("Authorization", "Bearer " + user[2])).andExpect(jsonPath("$.exists").value(true));
        mvc.perform(get("/api/v1/internal/accounts/" + UUID.randomUUID() + "/exists").header("X-Internal-Token", "internal-secret")
                .header("Authorization", "Bearer " + user[2])).andExpect(jsonPath("$.exists").value(false));
        mvc.perform(get("/api/v1/internal/accounts/" + UUID.randomUUID()).header("X-Internal-Token", "internal-secret")
                .header("Authorization", "Bearer " + user[2])).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/internal/accounts/lookup").param("email", " " + user[1].toUpperCase() + " ")
                        .header("X-Internal-Token", "internal-secret").header("Authorization", "Bearer " + user[2]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user[0]));
        mvc.perform(get("/api/v1/internal/accounts/lookup").param("email", "nobody@example.com")
                .header("X-Internal-Token", "internal-secret").header("Authorization", "Bearer " + user[2])).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/internal/accounts/lookup").param("email", " ")
                .header("X-Internal-Token", "internal-secret").header("Authorization", "Bearer " + user[2])).andExpect(status().isNotFound());
    }

    @Test
    void tokenIntrospectionReportsActiveRevokedAndForgedTokens() throws Exception {
        String[] user = register("intro");
        mvc.perform(get("/api/v1/internal/tokens/status").header("X-Internal-Token", "internal-secret")
                        .header("Authorization", "Bearer " + user[2]))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.subject").value(user[0]));
        mvc.perform(get("/api/v1/internal/tokens/status").header("X-Internal-Token", "internal-secret")
                .header("Authorization", "Bearer forged")).andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.subject").doesNotExist());
        mvc.perform(get("/api/v1/internal/tokens/status").header("X-Internal-Token", "internal-secret"))
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + user[2])).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/internal/tokens/status").header("X-Internal-Token", "internal-secret")
                .header("Authorization", "Bearer " + user[2])).andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void e2eFixturePromotesOnlyWithTheFixtureToken() throws Exception {
        String[] user = register("fixture");
        mvc.perform(post("/api/v1/internal/e2e/accounts/" + user[0] + "/realtor").header("X-Internal-Token", "internal-secret"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/internal/e2e/accounts/" + user[0] + "/realtor").header("X-Internal-Token", "internal-secret")
                .header("X-Fixture-Token", "wrong")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/internal/e2e/accounts/" + UUID.randomUUID() + "/realtor").header("X-Internal-Token", "internal-secret")
                .header("X-Fixture-Token", "test-fixture-token")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/internal/e2e/accounts/" + user[0] + "/realtor").header("X-Internal-Token", "internal-secret")
                        .header("X-Fixture-Token", "test-fixture-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_REALTOR"));
    }
}
