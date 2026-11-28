package com.fidiacorp.credicasa.catalog.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos BDD del catálogo contra la API real del microservicio (MockMvc + H2) con JWT de la librería compartida. */
@CucumberContextConfiguration
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:catalog_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
public class CatalogSteps {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.security.jwt.secret}") String secret;

    record User(String id, String email, String token) {}

    private User client;
    private User other;
    private User advisor;
    private String propertyId;
    private String certificateHash;
    private int lastStatus;
    private JsonNode last;

    private User user(String prefix, String role) {
        UUID id = UUID.randomUUID();
        String email = prefix + "-" + id + "@example.com";
        String token = new JwtTokenIssuer(secret, 3_600_000).issue(id, List.of(role), email, "Ana Perez");
        return new User(id.toString(), email, token);
    }

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

    private String property(User owner, String title, String address, String price, List<String> tags) throws Exception {
        return call(post("/api/v1/properties"), owner.token(), Map.of("title", title, "address", address, "price", price,
                "currency", "PEN", "tags", tags), 201).get("id").asText();
    }

    @Given("a client, a second client and an advisor authenticated with JWTs issued by the identity service")
    public void actors() {
        client = user("client", "ROLE_CLIENT");
        other = user("other", "ROLE_CLIENT");
        advisor = user("advisor", "ROLE_REALTOR");
        propertyId = null;
    }

    // US07
    @When("the client registers the property {string} priced {string} PEN with tags {string}")
    public void registerProperty(String title, String price, String tags) throws Exception {
        propertyId = property(client, title, "Lima, Perú", price, Arrays.stream(tags.split(",")).map(String::trim).toList());
    }

    @Then("the property is stored with its owner and can be read by its ID")
    public void stored() throws Exception {
        JsonNode property = call(get("/api/v1/properties/" + propertyId), other.token(), null, 200);
        assertThat(property.get("ownerId").asText()).isEqualTo(client.id());
        assertThat(property.get("price").asText()).isEqualTo("385000.00");
        assertThat(property.get("legitimacyStatus").asText()).isEqualTo("PENDING_VERIFICATION");
        assertThat(call(get("/api/v1/properties").param("mine", "true"), client.token(), null, 200).get("totalElements").asInt()).isEqualTo(1);
    }

    @When("the client submits a property without title and with a negative price")
    public void invalid() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", " ");
        body.put("address", "Lima");
        body.put("price", "-10.00");
        body.put("currency", "PEN");
        call(post("/api/v1/properties"), client.token(), body);
    }

    @Then("the property registration is rejected with validation errors for {string} and {string}")
    public void rejected(String first, String second) throws Exception {
        assertThat(lastStatus).isEqualTo(400);
        assertThat(last.get("validationErrors").has(first)).isTrue();
        assertThat(last.get("validationErrors").has(second)).isTrue();
        assertThat(call(get("/api/v1/properties").param("mine", "true"), client.token(), null, 200).get("totalElements").asInt()).isZero();
    }

    // US08
    @When("another client tries to delete the property before its owner deletes it")
    public void removeProperty() throws Exception {
        propertyId = property(client, "Casa privada", "Lima Central", "250000.00", List.of("terraza"));
        call(delete("/api/v1/properties/" + propertyId), other.token(), null, 403);
        call(delete("/api/v1/properties/" + propertyId), advisor.token(), null, 403);
        call(get("/api/v1/properties/" + propertyId), client.token(), null, 200);
        call(delete("/api/v1/properties/" + propertyId), client.token(), null, 204);
    }

    @Then("the deleted property is no longer in the catalogue or available by ID")
    public void removed() throws Exception {
        call(get("/api/v1/properties/" + propertyId), client.token(), null, 404);
        call(delete("/api/v1/properties/" + propertyId), client.token(), null, 404);
        assertThat(call(get("/api/v1/properties").param("mine", "true"), client.token(), null, 200).get("items")).isEmpty();
    }

    // US09
    @When("the client registers a property and verifies registry with entry {string}")
    public void verify(String entry) throws Exception {
        propertyId = property(client, "Departamento Legítimo", "Av. Salaverry 1240, Jesús María", "320000.00", List.of("terraza"));
        call(post("/api/v1/properties/" + propertyId + "/verify-registry"), client.token(),
                Map.of("registryNumber", entry, "claimedOwner", client.email()), 200);
    }

    @Then("the property legitimacy report is verified with a valid certificate hash")
    public void verified() throws Exception {
        assertThat(last.get("status").asText()).isEqualTo("VERIFIED_GENUINE");
        assertThat(last.get("genuine").asBoolean()).isTrue();
        String payload = propertyId + "|" + last.get("registryNumber").asText() + "|" + client.email() + "|VERIFIED_GENUINE|"
                + last.get("verifiedAt").asText();
        certificateHash = last.get("certificateHash").asText();
        assertThat(certificateHash).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(payload.getBytes(StandardCharsets.UTF_8))));
        assertThat(call(get("/api/v1/properties/" + propertyId + "/legitimacy"), other.token(), null, 200)
                .get("certificateHash").asText()).isEqualTo(certificateHash);
    }

    @And("the catalogue card of the property shows the verified badge")
    public void badge() throws Exception {
        JsonNode card = call(get("/api/v1/properties").param("mine", "true"), client.token(), null, 200).get("items").get(0);
        assertThat(card.get("legitimacyStatus").asText()).isEqualTo("VERIFIED_GENUINE");
        assertThat(card.get("certificateHash").asText()).isEqualTo(certificateHash);
    }

    @When("a registry verification is requested for a non-existent property ID")
    public void missing() throws Exception {
        call(post("/api/v1/properties/" + UUID.randomUUID() + "/verify-registry"), client.token(),
                Map.of("registryNumber", "14298172", "claimedOwner", client.email()));
    }

    @Then("the registry verification attempt returns status {int}")
    public void attemptStatus(int status) {
        assertThat(lastStatus).isEqualTo(status);
    }

    @When("the other client tries to verify the registry of the client's property")
    public void foreign() throws Exception {
        propertyId = property(client, "Inmueble ajeno", "Lince", "250000.00", List.of());
        call(post("/api/v1/properties/" + propertyId + "/verify-registry"), other.token(), Map.of("registryNumber", "12345678", "claimedOwner", "Atacante"));
    }

    @And("the property remains pending verification")
    public void pending() throws Exception {
        assertThat(call(get("/api/v1/properties/" + propertyId + "/legitimacy"), client.token(), null, 200).get("status").asText())
                .isEqualTo("PENDING_VERIFICATION");
    }

    // US13
    @And("the client searches the catalogue for {string}")
    public void search(String query) throws Exception {
        call(get("/api/v1/properties").param("q", query).param("mine", "true"), client.token(), null, 200);
    }

    @Then("the search returns the property {string}")
    public void found(String title) {
        assertThat(last.get("totalElements").asInt()).isEqualTo(1);
        assertThat(last.get("items").get(0).get("title").asText()).isEqualTo(title);
    }

    @Then("the search returns no properties")
    public void none() {
        assertThat(last.get("totalElements").asInt()).isZero();
        assertThat(last.get("items")).isEmpty();
    }

    // US14
    @When("properties with overlapping tags are registered")
    public void tags() throws Exception {
        property(client, "Casa matching", "Lima Central", "250000.00", List.of(" ＴＥＲＲＡＺＡ ", "garaje"));
        property(client, "Casa missing", "Lima Central", "250000.00", List.of("terraza"));
        property(other, "Casa ajena", "Lima Central", "250000.00", List.of("terraza", "garaje"));
    }

    @Then("the search returns only properties matching every requested tag and owner")
    public void matching() throws Exception {
        JsonNode result = call(get("/api/v1/properties").param("tags", "TERRAZA", "garaje").param("q", "matching").param("mine", "true"),
                client.token(), null, 200);
        assertThat(result.get("totalElements").asInt()).isEqualTo(1);
        assertThat(result.get("items").get(0).get("title").asText()).isEqualTo("Casa matching");
        assertThat(result.get("items").get(0).get("tags")).hasSize(2);
    }

    // US15
    @When("the client queries AI recommendations with monthly income {string} and down payment {string}")
    public void ai(String income, String downPayment) throws Exception {
        call(post("/api/v1/ai/recommendations"), client.token(), Map.of("monthlyIncome", new BigDecimal(income),
                "downPaymentAvailable", new BigDecimal(downPayment), "currency", "PEN", "preferredDistrict", "", "desiredTermYears", 20,
                "preferenceTags", List.of()), 200);
    }

    @Then("the AI borrower evaluation limits installment to {string} and explains the 35 percent SBS rule")
    public void aiLimit(String expected) {
        assertThat(new BigDecimal(last.get("maxAllowableInstallment").asText())).isEqualByComparingTo(expected);
        assertThat(last.get("evaluationSummary").asText()).contains("35%");
    }

    @When("properties in Miraflores and Surco are registered")
    public void districts() throws Exception {
        property(advisor, "Depa Miraflores", "Av. Benavides 1200, Miraflores", "350000.00", List.of("terraza", "miraflores"));
        property(advisor, "Casa Surco", "Av. Higuereta 400, Surco", "420000.00", List.of("jardin", "surco"));
    }

    @And("the client requests AI recommendations for district {string} with tag {string}")
    public void aiDistrict(String district, String tag) throws Exception {
        call(post("/api/v1/ai/recommendations"), client.token(), Map.of("monthlyIncome", new BigDecimal("9500.00"),
                "downPaymentAvailable", new BigDecimal("70000.00"), "currency", "PEN", "preferredDistrict", district, "desiredTermYears", 20,
                "preferenceTags", List.of(tag)), 200);
    }

    @Then("the Miraflores property ranks first with a high compatibility score")
    public void ranking() {
        JsonNode top = last.get("recommendations").get(0);
        assertThat(top.get("title").asText()).isEqualTo("Depa Miraflores");
        assertThat(top.get("relevanceScore").asInt()).isGreaterThanOrEqualTo(50);
        assertThat(top.get("recommendationReason").asText()).containsIgnoringCase("miraflores");
    }
}
