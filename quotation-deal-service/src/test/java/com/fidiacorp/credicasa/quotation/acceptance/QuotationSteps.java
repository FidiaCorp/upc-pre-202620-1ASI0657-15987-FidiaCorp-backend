package com.fidiacorp.credicasa.quotation.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.CreditOfferJpaRepository;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.fidiacorp.credicasa.quotation.testing.Downstreams;
import com.fidiacorp.credicasa.quotation.testing.Downstreams.Actor;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@CucumberContextConfiguration
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quotation_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000")
@AutoConfigureMockMvc
public class QuotationSteps {
    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        Downstreams.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CreditOfferJpaRepository offers;
    @Autowired OutboxEventJpaRepository outbox;
    Actor advisor;
    Actor client;
    UUID propertyId;
    String offerId;
    long offersBefore;
    int status;
    JsonNode last;
    MvcResult raw;
    JsonNode firstSchedule;

    private JsonNode call(MockHttpServletRequestBuilder request, Actor actor, Object body) throws Exception {
        request.header("Authorization", "Bearer " + actor.token());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        raw = mvc.perform(request).andReturn();
        status = raw.getResponse().getStatus();
        String text = raw.getResponse().getContentAsString(StandardCharsets.UTF_8);
        last = text.isBlank() || !(text.startsWith("{") || text.startsWith("[")) ? json.nullNode() : json.readTree(text);
        return last;
    }

    @Given("an advisor owning a property of {string} PEN and a registered client")
    public void actors(String price) {
        Downstreams.reset();
        advisor = Downstreams.actor("advisor", "ROLE_REALTOR");
        client = Downstreams.actor("client", "ROLE_CLIENT");
        Downstreams.account(client, "ROLE_CLIENT");
        propertyId = Downstreams.property(advisor, price, "PEN");
        offersBefore = offers.count();
    }

    private Map<String, Object> offer(String email, String amount) {
        return Map.of("recipientEmail", email, "propertyId", propertyId, "bankCode", "BCP", "loanAmount", amount, "currency", "PEN",
                "annualInterestRate", "0.08512345", "termMonths", 12, "startDate", "2026-01-15");
    }

    @When("the advisor issues a BCP offer of {string} to the client")
    public void issue(String amount) throws Exception {
        offerId = call(post("/api/v1/offers"), advisor, offer(client.email(), amount)).get("id").asText();
        assertThat(status).isEqualTo(201);
    }

    @When("the advisor issues a BCP offer of {string} to an unknown email")
    public void unknown(String amount) throws Exception {
        call(post("/api/v1/offers"), advisor, offer("ghost-" + UUID.randomUUID() + "@example.com", amount));
    }

    @Then("the offer request is rejected with status {int} and nothing is stored")
    public void rejected(int expected) {
        assertThat(status).isEqualTo(expected);
        assertThat(offers.count()).isEqualTo(offersBefore);
    }

    @Then("the client sees the offer in the received list")
    public void received() throws Exception {
        JsonNode page = call(get("/api/v1/offers"), client, null);
        assertThat(page.get("items").findValuesAsText("id")).contains(offerId);
        assertThat(page.get("items").get(0).get("title").asText()).isEqualTo("Oferta demostrativa de Banco de Crédito del Perú");
    }

    @And("the outbox holds a {string} event addressed to the client")
    public void outboxEvent(String type) throws Exception {
        boolean found = false;
        for (var event : outbox.findAll()) {
            if (event.eventType.equals(type) && event.aggregateId.toString().equals(offerId)) {
                found |= json.readTree(event.payload).get("recipientId").asText().equals(client.id().toString());
            }
        }
        assertThat(found).isTrue();
    }

    @And("the client generates the offer schedule twice")
    public void scheduleTwice() throws Exception {
        firstSchedule = call(post("/api/v1/offers/" + offerId + "/schedule"), client, null);
        call(post("/api/v1/offers/" + offerId + "/schedule"), client, null);
    }

    @Then("both responses are the same {int} installment schedule ending at balance {string}")
    public void sameSchedule(int count, String balance) {
        assertThat(status).isEqualTo(200);
        assertThat(last).isEqualTo(firstSchedule);
        assertThat(last.get("schedule")).hasSize(count);
        assertThat(last.get("schedule").get(count - 1).get("finalBalance").asText()).isEqualTo(balance);
    }

    @Then("the client downloads the {string} export of version {int}")
    public void download(String format, int version) throws Exception {
        call(get("/api/v1/offers/" + offerId + "/schedule/export/" + format).param("version", Integer.toString(version)), client, null);
        assertThat(status).isEqualTo(200);
        byte[] body = raw.getResponse().getContentAsByteArray();
        assertThat(new String(body, 0, format.equals("pdf") ? 5 : 2, StandardCharsets.US_ASCII)).isEqualTo(format.equals("pdf") ? "%PDF-" : "PK");
        assertThat(raw.getResponse().getHeader("X-Quotation-Version")).isEqualTo(Integer.toString(version));
        assertThat(raw.getResponse().getHeader("X-Snapshot-Sha256")).matches("[0-9a-f]{64}");
    }

    @And("the export history lists {int} completed jobs sharing the same snapshot hash")
    public void history(int count) throws Exception {
        JsonNode jobs = call(get("/api/v1/offers/" + offerId + "/exports"), advisor, null);
        assertThat(jobs).hasSize(count);
        assertThat(jobs.findValuesAsText("status")).containsOnly("COMPLETED");
        assertThat(new HashSet<>(jobs.findValuesAsText("snapshotHash"))).hasSize(1);
        assertThat(new HashSet<>(jobs.findValuesAsText("format"))).containsExactlyInAnyOrder("PDF", "XLSX");
    }

    @And("the advisor issues version {int} with rate {string} valid for {int} days")
    public void version(int number, String rate, int days) throws Exception {
        JsonNode created = call(post("/api/v1/offers/" + offerId + "/versions"), advisor, Map.of("annualInterestRate", rate,
                "loanAmount", "190000.00", "validityDays", days));
        assertThat(status).isEqualTo(201);
        assertThat(created.get("versionNumber").asInt()).isEqualTo(number);
    }

    @Then("the client sees {int} versions with distinct SHA-256 hashes and version {int} first")
    public void versions(int count, int first) throws Exception {
        JsonNode list = call(get("/api/v1/offers/" + offerId + "/versions"), client, null);
        assertThat(list).hasSize(count);
        assertThat(list.get(0).get("versionNumber").asInt()).isEqualTo(first);
        assertThat(new HashSet<>(list.findValuesAsText("snapshotHash"))).hasSize(count);
        assertThat(list.findValuesAsText("intact")).containsOnly("true");
    }

    @And("version {int} still keeps its original rate {string}")
    public void original(int number, String rate) throws Exception {
        assertThat(call(get("/api/v1/offers/" + offerId + "/versions/" + number), client, null).get("snapshot").get("terms")
                .get("annualInterestRate").asText()).isEqualTo(rate);
    }
}
