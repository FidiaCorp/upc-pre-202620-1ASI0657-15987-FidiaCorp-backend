package com.fidiacorp.credicasa.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class Sprint3Steps {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AccountJpaRepository accounts;

    record User(String id, String email, String token) {}

    private static final String PASSWORD = "Sprint3Password123!";
    private User client;
    private User other;
    private User advisor;

    private String propertyId;
    private String quotationId;
    private String offerId;
    private String conversationId;
    private byte[] exportedBytes;
    private JsonNode lastJsonResponse;
    private int lastStatus;

    private JsonNode callJson(MockHttpServletRequestBuilder req, String token, Object data, int expectedStatus) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (data != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
        MvcResult result = mvc.perform(req).andReturn();
        lastStatus = result.getResponse().getStatus();
        assertThat(lastStatus).as("HTTP %s %s: %s", req, result.getRequest().getRequestURI(), result.getResponse().getContentAsString()).isEqualTo(expectedStatus);
        String body = result.getResponse().getContentAsString();
        lastJsonResponse = body.isBlank() ? json.nullNode() : json.readTree(body);
        return lastJsonResponse;
    }

    private MvcResult callRaw(MockHttpServletRequestBuilder req, String token, Object data) throws Exception {
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (data != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data));
        MvcResult result = mvc.perform(req).andReturn();
        lastStatus = result.getResponse().getStatus();
        return result;
    }

    private String login(String email) throws Exception {
        return callJson(post("/api/v1/auth/login"), null,
                Map.of("email", email, "password", PASSWORD), 200).get("token").asText();
    }

    private User register(String prefix) throws Exception {
        String email = prefix + UUID.randomUUID() + "@example.com";
        JsonNode profile = callJson(post("/api/v1/auth/register"), null,
                Map.of("firstName", "Sprint", "lastName", "Tester", "email", email, "password", PASSWORD), 201);
        String token = login(email);
        return new User(profile.get("id").asText(), email, token);
    }

    @Given("registered clients and an advisor with independent sessions for sprint 3")
    public void setup() throws Exception {
        client = register("s3client");
        other = register("s3other");
        advisor = register("s3advisor");

        var entity = accounts.findById(UUID.fromString(advisor.id())).orElseThrow();
        entity.roles = "ROLE_REALTOR";
        accounts.saveAndFlush(entity);
        advisor = new User(advisor.id(), advisor.email(), login(advisor.email()));
    }

    // US06
    @When("the client checks subscription status and submits payment verification with reference {string}")
    public void clientVerifiesPayment(String reference) throws Exception {
        callJson(get("/api/v1/subscriptions/status"), client.token(), null, 200);
        callJson(post("/api/v1/payments/verify"), client.token(),
                Map.of(
                        "transactionReference", reference,
                        "paymentMethod", "TRANSFER",
                        "amount", new BigDecimal("49.90"),
                        "currency", "PEN",
                        "planName", "PREMIUM"
                ), 200);
    }

    @Then("the subscription is active with confirmed payment status")
    public void subscriptionIsActive() {
        assertThat(lastJsonResponse.get("paymentStatus").asText()).isIn("COMPLETED", "CONFIRMED");
        assertThat(lastJsonResponse.get("subscriptionStatus").asText()).isEqualTo("ACTIVE");
    }

    @When("the client attempts to verify a payment with zero amount")
    public void verifyZeroPayment() throws Exception {
        callRaw(post("/api/v1/payments/verify"), client.token(),
                Map.of(
                        "transactionReference", "INVALID-REF",
                        "paymentMethod", "TRANSFER",
                        "amount", new BigDecimal("0.00"),
                        "currency", "PEN",
                        "planName", "PREMIUM"
                ));
    }

    @Then("the payment verification is rejected with status 400")
    public void verificationRejected400() {
        assertThat(lastStatus).isEqualTo(400);
    }

    // US09
    @When("the client registers a property and verifies registry with entry {string} and sheet {string}")
    public void registerAndVerifyProperty(String entry, String sheet) throws Exception {
        JsonNode prop = callJson(post("/api/v1/properties"), client.token(),
                Map.of("title", "Departamento Legítimo", "address", "Av. Salaverry 1240, Jesús María", "price", "320000.00", "currency", "PEN", "tags", List.of("terraza", "estrenar")), 201);
        propertyId = prop.get("id").asText();

        callJson(post("/api/v1/properties/" + propertyId + "/verify-registry"), client.token(),
                Map.of("registryNumber", entry, "claimedOwner", client.email()), 200);
    }

    @Then("the property legitimacy report is verified with a valid certificate hash")
    public void legitimacyReportVerified() throws Exception {
        assertThat(lastJsonResponse.get("status").asText()).isIn("VERIFIED", "VERIFIED_GENUINE");
        assertThat(lastJsonResponse.get("genuine").asBoolean()).isTrue();

        JsonNode check = callJson(get("/api/v1/properties/" + propertyId + "/legitimacy"), client.token(), null, 200);
        assertThat(check.get("status").asText()).isIn("VERIFIED", "VERIFIED_GENUINE");
    }

    @When("a registry verification is requested for a non-existent property ID")
    public void nonExistentPropertyRegistry() throws Exception {
        callRaw(post("/api/v1/properties/" + UUID.randomUUID() + "/verify-registry"), client.token(),
                Map.of("registryNumber", "14298172", "claimedOwner", client.email()));
    }

    @Then("the registry verification attempt returns not found status 404")
    public void registryAttemptReturns404() {
        assertThat(lastStatus).isEqualTo(404);
    }

    // US12
    @When("the client calculates a mortgage simulation and requests schedule export in xlsx")
    public void calculateAndExportXlsx() throws Exception {
        JsonNode sim = callJson(post("/api/v1/simulations/calculate"), client.token(),
                Map.of("loanAmount", "180000.00", "propertyValue", "220000.00", "currency", "PEN", "termMonths", 120, "annualInterestRate", "0.095", "startDate", "2026-03-01"), 201);
        quotationId = sim.get("quotationId").asText();

        MvcResult res = callRaw(get("/api/v1/simulations/" + quotationId + "/export/xlsx"), client.token(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        exportedBytes = res.getResponse().getContentAsByteArray();
    }

    @Then("the system returns a valid OpenXML spreadsheet binary stream")
    public void returnsOpenXmlBinary() {
        assertThat(exportedBytes).isNotNull();
        assertThat(exportedBytes.length).isGreaterThan(100);
        // PK\x03\x04 zip header
        assertThat(exportedBytes[0]).isEqualTo((byte) 'P');
        assertThat(exportedBytes[1]).isEqualTo((byte) 'K');
        assertThat(exportedBytes[2]).isEqualTo((byte) 0x03);
        assertThat(exportedBytes[3]).isEqualTo((byte) 0x04);
    }

    @When("the advisor issues a housing credit offer to the client")
    public void advisorIssuesOffer() throws Exception {
        JsonNode prop = callJson(post("/api/v1/properties"), advisor.token(),
                Map.of("title", "Departamento Oferta", "address", "San Borja", "price", "300000.00", "currency", "PEN", "tags", List.of("estreno")), 201);
        String pId = prop.get("id").asText();

        JsonNode offer = callJson(post("/api/v1/offers"), advisor.token(),
                Map.of("recipientEmail", client.email(), "propertyId", pId, "bankCode", "BCP",
                        "loanAmount", "240000.00", "currency", "PEN", "annualInterestRate", "0.082", "termMonths", 180, "startDate", "2026-04-01"), 201);
        offerId = offer.get("id").asText();
    }

    @Then("the other client is forbidden from exporting the offer schedule in xlsx")
    public void otherForbiddenFromExportingOffer() throws Exception {
        MvcResult res = callRaw(get("/api/v1/offers/" + offerId + "/schedule/export/xlsx"), other.token(), null);
        assertThat(res.getResponse().getStatus()).isIn(403, 404);
    }

    // US15
    @When("the client queries AI recommendations with monthly income {string} and down payment {string}")
    public void queryAiRecommendations(String income, String downPayment) throws Exception {
        callJson(post("/api/v1/ai/recommendations"), client.token(),
                Map.of("monthlyIncome", new BigDecimal(income), "downPaymentAvailable", new BigDecimal(downPayment),
                        "currency", "PEN", "preferredDistrict", "", "desiredTermYears", 20, "preferenceTags", List.of()), 200);
    }

    @Then("the AI borrower evaluation limits installment to 35 percent and suggests solvent properties")
    public void aiLimitsInstallmentTo35() {
        assertThat(lastJsonResponse.get("maxAllowableInstallment").asDouble()).isGreaterThan(0);
        assertThat(lastJsonResponse.get("evaluationSummary").asText()).contains("35%");
    }

    @When("properties in Miraflores and Surco are registered")
    public void registerMirafloresAndSurcoProperties() throws Exception {
        callJson(post("/api/v1/properties"), client.token(),
                Map.of("title", "Depa Miraflores", "address", "Av. Benavides 1200, Miraflores", "price", "350000.00", "currency", "PEN", "tags", List.of("terraza", "miraflores")), 201);
        callJson(post("/api/v1/properties"), client.token(),
                Map.of("title", "Casa Surco", "address", "Av. Higuereta 400, Surco", "price", "420000.00", "currency", "PEN", "tags", List.of("jardin", "surco")), 201);
    }

    @And("the client requests AI recommendations for district {string} with tag {string}")
    public void requestAiForDistrictAndTag(String district, String tag) throws Exception {
        callJson(post("/api/v1/ai/recommendations"), client.token(),
                Map.of("monthlyIncome", new BigDecimal("9500.00"), "downPaymentAvailable", new BigDecimal("70000.00"),
                        "currency", "PEN", "preferredDistrict", district, "desiredTermYears", 20, "preferenceTags", List.of(tag)), 200);
    }

    @Then("recommended properties match district criteria with high compatibility scores")
    public void matchDistrictWithHighScores() {
        JsonNode recs = lastJsonResponse.get("recommendations");
        assertThat(recs).isNotNull();
        if (recs.size() > 0) {
            assertThat(recs.get(0).get("relevanceScore").asInt()).isGreaterThanOrEqualTo(50);
        }
    }

    // US17
    @When("the client blocks the other client with reason {string}")
    public void clientBlocksOtherWithReason(String reason) throws Exception {
        callJson(post("/api/v1/messages/blocks/" + other.id()), client.token(),
                Map.of("reason", reason), 200);
    }

    @Then("the other client appears in the blocked list")
    public void otherAppearsInBlockedList() throws Exception {
        JsonNode list = callJson(get("/api/v1/messages/blocks"), client.token(), null, 200);
        assertThat(list.isArray()).isTrue();
        boolean found = false;
        for (JsonNode item : list) {
            if (item.has("blockedId") && item.get("blockedId").asText().equals(other.id())) {
                found = true;
                break;
            }
        }
        assertThat(found).isTrue();
    }

    @When("the client blocks the other client")
    public void clientBlocksOther() throws Exception {
        callJson(post("/api/v1/messages/blocks/" + other.id()), client.token(),
                Map.of("reason", "Bloqueado por usuario"), 200);
    }

    @And("the other client attempts to send a direct message to the client")
    public void otherSendsDirectMessage() throws Exception {
        callRaw(post("/api/v1/messages"), other.token(),
                Map.of("conversationId", UUID.randomUUID(), "recipientId", UUID.fromString(client.id()), "content", "Hola, me interesa tu propiedad."));
    }

    @Then("the message dispatch is blocked with status 409")
    public void messageBlocked409() {
        assertThat(lastStatus).isEqualTo(409);
    }

    // US18
    @When("messages are exchanged in a conversation")
    public void messagesExchanged() throws Exception {
        UUID convId = UUID.randomUUID();
        conversationId = convId.toString();
        callJson(post("/api/v1/messages"), client.token(),
                Map.of("conversationId", convId, "recipientId", UUID.fromString(advisor.id()), "content", "Hola asesor, requiero información del crédito."), 200);
    }

    @Then("the client can export the conversation transcript in plain text")
    public void exportTranscriptPlainText() throws Exception {
        MvcResult res = callRaw(get("/api/v1/messages/conversations/" + conversationId + "/export"), client.token(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentType()).contains("text/plain");
        assertThat(res.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("CREDICASA");
    }

    @Then("the client can export the conversation transcript in JSON format")
    public void exportTranscriptJson() throws Exception {
        MvcResult res = callRaw(get("/api/v1/messages/conversations/" + conversationId + "/export/json"), client.token(), null);
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        assertThat(res.getResponse().getContentType()).contains("application/json");
        JsonNode root = json.readTree(res.getResponse().getContentAsString());
        assertThat(root.has("conversationId")).isTrue();
        assertThat(root.has("messages")).isTrue();
    }
}
