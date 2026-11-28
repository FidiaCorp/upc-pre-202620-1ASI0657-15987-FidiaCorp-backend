package com.fidiacorp.credicasa.identity.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.identity.domain.service.TotpAlgorithm;
import com.fidiacorp.credicasa.identity.infrastructure.persistence.repository.AccountJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** Pasos BDD US01–US06 contra la API real del microservicio (MockMvc + H2, reloj fijo para TOTP). */
@CucumberContextConfiguration
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:identity_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "app.security.jwt.secret=acceptance-only-test-signing-key-at-least-thirty-two-bytes",
        "app.security.two-factor.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
@AutoConfigureMockMvc
@Import(IdentitySteps.AcceptanceTime.class)
public class IdentitySteps {
    @TestConfiguration
    public static class AcceptanceTime {
        private final Instant start = Instant.parse("2026-10-28T12:00:00Z");
        private long offsetSeconds;

        @Bean
        @Primary
        Clock acceptanceClock() {
            AcceptanceTime self = this;
            return new Clock() {
                public ZoneOffset getZone() { return ZoneOffset.UTC; }
                public Clock withZone(java.time.ZoneId zone) { return this; }
                public Instant instant() { return start.plusSeconds(self.offsetSeconds); }
            };
        }

        void advance(long seconds) {
            offsetSeconds += seconds;
        }
    }

    private static final String PASSWORD = "IdentityAcceptance2026!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired TotpAlgorithm totp;
    @Autowired Clock clock;
    @Autowired AcceptanceTime time;

    record User(String id, String email, String token) {}

    private User client;
    private User other;
    private User advisor;
    private String secondToken;
    private String totpSecret;
    private String transactionReference;
    private String paymentGateway;
    private int lastStatus;
    private JsonNode last;

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        lastStatus = result.getResponse().getStatus();
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        last = text.isBlank() ? json.nullNode() : json.readTree(text);
        return last;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body, int expected) throws Exception {
        JsonNode node = call(request, token, body);
        assertThat(lastStatus).as("%s → %s", request, node).isEqualTo(expected);
        return node;
    }

    private String login(String email) throws Exception {
        return call(post("/api/v1/auth/login"), null, Map.of("email", email, "password", PASSWORD), 200).get("token").asText();
    }

    private User register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        JsonNode profile = call(post("/api/v1/auth/register"), null,
                Map.of("firstName", "Ana", "lastName", "Perez", "email", email, "password", PASSWORD), 201);
        return new User(profile.get("id").asText(), email, login(email));
    }

    @Given("a client, a second client and an advisor registered in the identity service")
    public void actors() throws Exception {
        time.advance(120);
        client = register("client");
        other = register("other");
        advisor = register("advisor");
        var entity = accounts.findById(UUID.fromString(advisor.id())).orElseThrow();
        entity.roles = "ROLE_REALTOR";
        accounts.saveAndFlush(entity);
        advisor = new User(advisor.id(), advisor.email(), login(advisor.email()));
        transactionReference = null;
    }

    // US01
    @When("the client logs in with the correct email in upper case and password")
    public void correctLogin() throws Exception {
        call(post("/api/v1/auth/login"), null, Map.of("email", client.email().toUpperCase(), "password", PASSWORD));
    }

    @Then("a bearer token carrying the account identity is issued and the profile can be read with it")
    public void tokenIssued() throws Exception {
        assertThat(lastStatus).isEqualTo(200);
        assertThat(last.get("type").asText()).isEqualTo("Bearer");
        String issued = last.get("token").asText();
        JsonNode payload = json.readTree(Base64.getUrlDecoder().decode(issued.split("\\.")[1]));
        assertThat(payload.get("sub").asText()).isEqualTo(client.id());
        assertThat(payload.get("email").asText()).isEqualTo(client.email());
        assertThat(payload.get("name").asText()).isEqualTo("Ana Perez");
        assertThat(call(get("/api/v1/users/me"), issued, null, 200).get("id").asText()).isEqualTo(client.id());
        JsonNode advisorToken = json.readTree(Base64.getUrlDecoder().decode(advisor.token().split("\\.")[1]));
        assertThat(advisorToken.get("roles").get(0).asText()).isEqualTo("ROLE_REALTOR");
    }

    @When("the client logs in with a wrong password")
    public void wrongLogin() throws Exception {
        call(post("/api/v1/auth/login"), null, Map.of("email", client.email(), "password", "WrongPassword2026!"));
    }

    @Then("the login is rejected with status 401 and no token is issued")
    public void loginRejected() {
        assertThat(lastStatus).isEqualTo(401);
        assertThat(last.has("token")).isFalse();
        assertThat(last.get("detail").asText()).contains("Credenciales inválidas");
    }

    // US02
    @When("the client submits an invalid profile then saves a valid profile")
    public void profile() throws Exception {
        call(put("/api/v1/users/me"), client.token(), Map.of("firstName", " ", "lastName", "Perez", "email", client.email()), 400);
        assertThat(call(get("/api/v1/users/me"), client.token(), null, 200).get("firstName").asText()).isEqualTo("Ana");
        call(put("/api/v1/users/me"), client.token(), Map.of("firstName", "Elena", "lastName", "Actualizada", "email", client.email()), 200);
    }

    @Then("a new login reads the saved profile and the other client remains unchanged")
    public void persistedProfile() throws Exception {
        assertThat(call(get("/api/v1/users/me"), login(client.email()), null, 200).get("firstName").asText()).isEqualTo("Elena");
        assertThat(call(get("/api/v1/users/me"), other.token(), null, 200).get("firstName").asText()).isEqualTo("Ana");
    }

    // US03
    @When("the client configures TOTP and tries an invalid code followed by a valid code")
    public void enableTotp() throws Exception {
        totpSecret = call(post("/api/v1/users/me/two-factor/setup"), client.token(), Map.of("password", PASSWORD), 200).get("secret").asText();
        String code = totp.code(totpSecret, clock.instant().getEpochSecond() / 30);
        call(post("/api/v1/users/me/two-factor/confirm"), client.token(), Map.of("otp", code.equals("000000") ? "111111" : "000000"), 401);
        assertThat(call(get("/api/v1/users/me/two-factor"), client.token(), null, 200).get("enabled").asBoolean()).isFalse();
        call(post("/api/v1/users/me/two-factor/confirm"), client.token(), Map.of("otp", code), 200);
    }

    @Then("login without the second factor issues no token")
    public void noJwt() throws Exception {
        JsonNode result = call(post("/api/v1/auth/login"), null, Map.of("email", client.email(), "password", PASSWORD), 401);
        assertThat(result.get("code").asText()).isEqualTo("TWO_FACTOR_REQUIRED");
        assertThat(result.has("token")).isFalse();
    }

    @And("login with a fresh valid code issues a token")
    public void loginWithOtp() throws Exception {
        time.advance(30);
        String code = totp.code(totpSecret, clock.instant().getEpochSecond() / 30);
        assertThat(call(post("/api/v1/auth/login"), null, Map.of("email", client.email(), "password", PASSWORD, "otp", code), 200)
                .get("token").asText()).isNotBlank();
    }

    // US04
    @When("the client closes one of two active sessions")
    public void logout() throws Exception {
        secondToken = login(client.email());
        call(post("/api/v1/auth/logout"), client.token(), null, 204);
    }

    @Then("the closed session is rejected and the other session can read its profile")
    public void revoked() throws Exception {
        call(get("/api/v1/users/me"), client.token(), null, 401);
        call(get("/api/v1/users/me"), secondToken, null, 200);
    }

    @And("the token introspection used by the API Gateway reports the closed session as inactive")
    public void introspection() throws Exception {
        assertThat(call(get("/api/v1/internal/tokens/status"), client.token(), null, 200).get("active").asBoolean()).isFalse();
        JsonNode active = call(get("/api/v1/internal/tokens/status"), secondToken, null, 200);
        assertThat(active.get("active").asBoolean()).isTrue();
        assertThat(active.get("subject").asText()).isEqualTo(client.id());
    }

    // US05
    @When("the client lists the available payment gateways")
    public void listGateways() throws Exception {
        JsonNode gateways = call(get("/api/v1/payments/gateways"), client.token(), null, 200);
        assertThat(gateways).hasSize(3);
        assertThat(gateways.findValuesAsText("code")).containsExactly("CULQI", "IZIPAY", "STRIPE");
    }

    @When("the client pays plan {string} through gateway {string} with card token {string}")
    public void pay(String plan, String gateway, String cardToken) throws Exception {
        call(post("/api/v1/payments/checkout"), client.token(), Map.of("gatewayCode", gateway, "planName", plan, "cardToken", cardToken));
        if (lastStatus == 200) transactionReference = last.get("transactionReference").asText();
        paymentGateway = gateway;
    }

    @Then("the payment is rejected with status {int} and decline code {string}")
    public void paymentRejected(int status, String declineCode) {
        assertThat(lastStatus).isEqualTo(status);
        assertThat(last.get("status").asText()).isEqualTo("REJECTED");
        assertThat(last.get("declineCode").asText()).isEqualTo(declineCode);
        assertThat(last.get("retryAllowed").asBoolean()).isTrue();
    }

    @Then("the gateway approves the charge with a signed transaction reference")
    public void paymentApproved() {
        assertThat(lastStatus).isEqualTo(200);
        assertThat(last.get("status").asText()).isEqualTo("APPROVED");
        assertThat(transactionReference).matches(paymentGateway + "-[0-9a-f]{32}-[0-9a-f]{40}");
    }

    // US06
    @And("the client verifies the approved payment for plan {string} with amount {string}")
    public void verify(String plan, String amount) throws Exception {
        call(post("/api/v1/payments/verify"), client.token(), Map.of("transactionReference", transactionReference,
                "paymentMethod", paymentGateway, "amount", new BigDecimal(amount), "currency", "PEN", "planName", plan));
    }

    @Then("the subscription is active with confirmed payment status and {int} payment")
    public void active(int payments) throws Exception {
        assertThat(lastStatus).isEqualTo(200);
        JsonNode status = call(get("/api/v1/subscriptions/status"), client.token(), null, 200);
        assertThat(status.get("status").asText()).isEqualTo("ACTIVE");
        assertThat(status.get("active").asBoolean()).isTrue();
        assertThat(status.get("paymentCount").asInt()).isEqualTo(payments);
    }

    @When("the client attempts to verify a payment with zero amount")
    public void zero() throws Exception {
        call(post("/api/v1/payments/verify"), client.token(), Map.of("transactionReference", "INVALID-REF",
                "paymentMethod", "CULQI", "amount", new BigDecimal("0.00"), "currency", "PEN", "planName", "PREMIUM"));
    }

    @Then("the payment verification is rejected with status {int}")
    public void rejected(int status) {
        assertThat(lastStatus).isEqualTo(status);
    }

    @And("the client subscription remains inactive")
    public void inactive() throws Exception {
        JsonNode status = call(get("/api/v1/subscriptions/status"), client.token(), null, 200);
        assertThat(status.get("active").asBoolean()).isFalse();
        assertThat(status.get("paymentCount").asInt()).isZero();
    }

    @When("the second client tries to verify the client's approved payment")
    public void reuse() throws Exception {
        call(post("/api/v1/payments/verify"), other.token(), Map.of("transactionReference", transactionReference,
                "paymentMethod", paymentGateway, "amount", new BigDecimal("99.90"), "currency", "PEN", "planName", "PRO"));
    }
}
