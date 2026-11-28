package com.fidiacorp.credicasa.identity.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** QA-03 (ASR-SEC) en identity: V01–V04 (autenticación rota) y V16–V18 (abuso de pagos) sin fuga de datos. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:identity_adversarial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class IdentityAdversarialIT {
    private static final String PASSWORD = "AdversarialSuite2026!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private int attacks;

    private MvcResult call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        return mvc.perform(request).andReturn();
    }

    private JsonNode ok(MockHttpServletRequestBuilder request, String token, Object body, int status) throws Exception {
        MvcResult result = call(request, token, body);
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(text).isEqualTo(status);
        return text.isBlank() ? json.nullNode() : json.readTree(text);
    }

    private void attack(String vector, MockHttpServletRequestBuilder request, String token, Object body, int expected, String... secrets) throws Exception {
        MvcResult result = call(request, token, body);
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(result.getResponse().getStatus()).as(vector + " → " + text).isEqualTo(expected);
        for (String secret : secrets) assertThat(text).as(vector + " no debe filtrar datos").doesNotContain(secret);
        attacks++;
    }

    private String[] register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String id = ok(post("/api/v1/auth/register"), null, Map.of("firstName", "Sec", "lastName", prefix, "email", email, "password", PASSWORD), 201)
                .get("id").asText();
        return new String[]{id, email, login(email)};
    }

    private String login(String email) throws Exception {
        return ok(post("/api/v1/auth/login"), null, Map.of("email", email, "password", PASSWORD), 200).get("token").asText();
    }

    @Test
    @DisplayName("QA-03 identity: autenticación rota y abuso de pagos son rechazados sin filtrar datos")
    void adversarialVectorsAreRejected() throws Exception {
        String[] victim = register("victim");
        String[] attacker = register("attacker");
        String reference = ok(post("/api/v1/payments/checkout"), victim[2], Map.of("gatewayCode", "CULQI", "planName", "PRO",
                "cardToken", "tok_visa_ok"), 200).get("transactionReference").asText();
        ok(post("/api/v1/payments/verify"), victim[2], Map.of("transactionReference", reference, "paymentMethod", "CULQI",
                "amount", "99.90", "currency", "PEN", "planName", "PRO"), 200);

        attack("V01 sin token", get("/api/v1/users/me"), null, null, 401);
        attack("V02 token manipulado", get("/api/v1/users/me"), victim[2].substring(0, victim[2].length() - 3) + "abc", null, 401);
        String revoked = login(victim[1]);
        ok(post("/api/v1/auth/logout"), revoked, null, 204);
        attack("V03 token revocado", get("/api/v1/users/me"), revoked, null, 401);
        attack("V04 credenciales inválidas", post("/api/v1/auth/login"), null, Map.of("email", victim[1], "password", "x-wrong-password"), 401);
        attack("V16 US06 reutilizar comprobante ajeno", post("/api/v1/payments/verify"), attacker[2], Map.of("transactionReference", reference,
                "paymentMethod", "CULQI", "amount", "99.90", "currency", "PEN", "planName", "PRO"), 409, victim[0]);
        attack("V17 US06 auto-activación con 0.01", post("/api/v1/payments/verify"), attacker[2], Map.of("transactionReference",
                "CULQI-" + "a".repeat(32) + "-" + "b".repeat(40), "paymentMethod", "CULQI", "amount", "0.01", "currency", "PEN", "planName", "PRO"), 422);
        attack("V18 US06 referencia forjada con tarifa correcta", post("/api/v1/payments/verify"), attacker[2], Map.of("transactionReference",
                "CULQI-" + "a".repeat(32) + "-" + "b".repeat(40), "paymentMethod", "CULQI", "amount", "99.90", "currency", "PEN", "planName", "PRO"), 422);
        attack("Vx registro con rol privilegiado ignora el rol", post("/api/v1/auth/register"), null, Map.of("firstName", "Mal", "lastName", "Actor",
                "email", "esc-" + UUID.randomUUID() + "@example.com", "password", PASSWORD, "roles", new String[]{"ROLE_REALTOR"}), 201);
        attack("Vy API interna inaccesible con token de usuario en ruta desconocida", get("/api/v1/internal/accounts/" + victim[0]),
                null, null, 401, victim[1]);
        assertThat(attacks).isEqualTo(9);
        assertThat(ok(get("/api/v1/subscriptions/status"), attacker[2], null, 200).get("active").asBoolean()).isFalse();
        assertThat(ok(get("/api/v1/subscriptions/status"), victim[2], null, 200).get("planName").asText()).isEqualTo("PRO");
    }
}
