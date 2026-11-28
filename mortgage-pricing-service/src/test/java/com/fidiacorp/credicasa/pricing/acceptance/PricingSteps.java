package com.fidiacorp.credicasa.pricing.acceptance;

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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Pasos BDD de pricing contra la API real del microservicio (MockMvc + H2) con JWT del shared kernel. */
@CucumberContextConfiguration
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pricing_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
public class PricingSteps {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Value("${app.security.jwt.secret}") String secret;

    private String client;
    private String other;
    private String simulationId;
    private int lastStatus;
    private JsonNode last;

    private String token() {
        UUID id = UUID.randomUUID();
        return new JwtTokenIssuer(secret, 3_600_000).issue(id, List.of("ROLE_CLIENT"), id + "@example.com", "Lucía Pérez");
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        lastStatus = result.getResponse().getStatus();
        String text = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        last = text.isBlank() || !text.startsWith("{") && !text.startsWith("[") ? json.nullNode() : json.readTree(text);
        return last;
    }

    private Map<String, Object> loan(String amount, String rate, int months) {
        Map<String, Object> body = new HashMap<>();
        body.put("loanAmount", amount);
        body.put("propertyValue", "300000.00");
        body.put("currency", "PEN");
        body.put("annualInterestRate", rate);
        body.put("termMonths", months);
        body.put("startDate", "2026-01-15");
        return body;
    }

    @Given("a client and a second client authenticated with JWTs issued by the identity service")
    public void actors() {
        client = token();
        other = token();
    }

    @When("the client simulates a loan of {string} PEN at TEA {string} for {int} months")
    public void simulateTea(String amount, String rate, int months) throws Exception {
        call(post("/api/v1/simulations/calculate"), client, loan(amount, rate, months));
        if (lastStatus == 201) simulationId = last.get("quotationId").asText();
    }

    @When("the client simulates a loan of {string} PEN at TNA {string} compounded {int} times per year for {int} months")
    public void simulateTna(String amount, String rate, int periods, int months) throws Exception {
        Map<String, Object> body = loan(amount, rate, months);
        body.put("rateType", "TNA");
        body.put("compoundingPeriodsPerYear", periods);
        call(post("/api/v1/simulations/calculate"), client, body);
        assertThat(lastStatus).as(last.toString()).isEqualTo(201);
        simulationId = last.get("quotationId").asText();
    }

    @And("retrieving that simulation exposes exactly the same rate terms and TEA")
    public void sameRateOnRetrieval() throws Exception {
        JsonNode created = last;
        call(get("/api/v1/simulations/" + simulationId), client, null);
        assertThat(lastStatus).isEqualTo(200);
        assertThat(last.get("rate")).isEqualTo(created.get("rate"));
        assertThat(last.get("metrics").get("tea")).isEqualTo(created.get("metrics").get("tea"));
    }

    @When("the client simulates a TNA loan without compounding periods")
    public void tnaWithoutPeriods() throws Exception {
        Map<String, Object> body = loan("200000.00", "0.12", 240);
        body.put("rateType", "TNA");
        call(post("/api/v1/simulations/calculate"), client, body);
    }

    @Then("the schedule has {int} installments and the final balance is {string}")
    public void schedule(int count, String balance) {
        assertThat(lastStatus).isEqualTo(201);
        assertThat(last.get("schedule")).hasSize(count);
        assertThat(last.get("schedule").get(count - 1).get("finalBalance").asText()).isEqualTo(balance);
        assertThat(last.get("rate").get("rateType").asText()).isEqualTo("TEA");
    }

    @And("the response exposes TCEA, VAN and TIR as exact decimal strings")
    public void metrics() {
        JsonNode m = last.get("metrics");
        for (String field : List.of("tcea", "van", "monthlyTir", "tem")) {
            assertThat(m.get(field).isTextual()).as(field).isTrue();
            assertThat(m.get(field).asText()).doesNotContain("E");
        }
        assertThat(new BigDecimal(m.get("tcea").asText())).isGreaterThanOrEqualTo(new BigDecimal("0.09"));
    }

    @Then("the simulation is rejected with status {int}")
    public void rejected(int status) {
        assertThat(lastStatus).isEqualTo(status);
        assertThat(last.get("type").asText()).startsWith("https://api.credicasa.pe/problems/");
    }

    @Then("the client downloads the schedule as a valid XLSX workbook")
    public void xlsx() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/simulations/" + simulationId + "/export/xlsx").header("Authorization", "Bearer " + client)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).contains("spreadsheetml");
        boolean sheet = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) sheet |= entry.getName().equals("xl/worksheets/sheet1.xml");
        }
        assertThat(sheet).isTrue();
    }

    @And("the second client cannot download that schedule")
    public void foreignExport() throws Exception {
        call(get("/api/v1/simulations/" + simulationId + "/export/xlsx"), other, null);
        assertThat(lastStatus).isEqualTo(404);
    }

    @Then("the simulation reports rate type {string} and effective annual rate {string}")
    public void rate(String type, String tea) {
        assertThat(last.get("rate").get("rateType").asText()).isEqualTo(type);
        assertThat(last.get("rate").get("inputRate").asText()).isEqualTo("0.12");
        assertThat(last.get("rate").get("compoundingPeriodsPerYear").asInt()).isEqualTo(360);
        assertThat(last.get("rate").get("effectiveAnnualRate").asText()).isEqualTo(tea);
        assertThat(last.get("metrics").get("tea").asText()).isEqualTo(tea);
    }

    @Then("the client lists {int} own simulations with the most recent first")
    public void list(int count) throws Exception {
        call(get("/api/v1/simulations").param("size", "10"), client, null);
        assertThat(lastStatus).isEqualTo(200);
        assertThat(last.get("totalElements").asInt()).isEqualTo(count);
        assertThat(last.get("items").get(0).get("rateType").asText()).isEqualTo("TNA");
        // Mismas representaciones que la respuesta de cálculo (no la escala 24 de las columnas NUMERIC).
        assertThat(last.get("items").get(0).get("inputRate").asText()).isEqualTo("0.12");
        assertThat(last.get("items").get(0).get("tea").asText()).isEqualTo("0.12682503");
        assertThat(last.get("items").get(1).get("inputRate").asText()).isEqualTo("0.09");
        assertThat(last.get("items").get(1).get("rateType").asText()).isEqualTo("TEA");
        call(get("/api/v1/simulations").param("size", "0"), client, null);
        assertThat(lastStatus).isEqualTo(400);
    }

    @And("the second client lists {int} own simulations")
    public void otherList(int count) throws Exception {
        assertThat(call(get("/api/v1/simulations"), other, null).get("totalElements").asInt()).isEqualTo(count);
    }

    @When("the client compares a TEA 9.5% scenario, a TNA 9% monthly scenario and a TEA 9.5% scenario with a {double} prepayment after installment {int}")
    public void compare(double amount, int after) throws Exception {
        Map<String, Object> tna = loan("200000.00", "0.09", 240);
        tna.put("rateType", "TNA");
        tna.put("compoundingPeriodsPerYear", 12);
        call(post("/api/v1/simulations/compare"), client, Map.of("scenarios", List.of(
                Map.of("label", "Banco A TEA", "simulation", loan("200000.00", "0.095", 240)),
                Map.of("label", "Banco B TNA", "simulation", tna),
                Map.of("label", "Banco A + prepago", "simulation", loan("200000.00", "0.095", 240),
                        "extraordinaryPayment", Map.of("afterInstallment", after, "amount", String.format(java.util.Locale.ROOT, "%.2f", amount),
                                "strategy", "REDUCE_TERM")))));
    }

    @Then("the comparison returns {int} scenarios with the TNA scenario best by TCEA")
    public void compared(int count) {
        assertThat(lastStatus).as(last.toString()).isEqualTo(200);
        assertThat(last.get("scenarios")).hasSize(count);
        assertThat(last.get("bestByTcea").asText()).isEqualTo("Banco B TNA");
        assertThat(last.get("scenarios").get(1).get("rate").get("effectiveAnnualRate").asText()).isEqualTo("0.09380690");
    }

    @And("the prepayment scenario shortens the term and saves interest")
    public void prepayment() {
        JsonNode prepaid = last.get("scenarios").get(2);
        assertThat(prepaid.get("effectiveTermMonths").asInt()).isLessThan(240);
        assertThat(new BigDecimal(prepaid.get("interestSavings").asText())).isPositive();
        assertThat(prepaid.get("finalBalance").asText()).isEqualTo("0.00");
        assertThat(prepaid.get("extraordinaryPayment").get("strategy").asText()).isEqualTo("REDUCE_TERM");
        assertThat(last.get("bestByTotalInterest").asText()).isEqualTo("Banco A + prepago");
    }
}
