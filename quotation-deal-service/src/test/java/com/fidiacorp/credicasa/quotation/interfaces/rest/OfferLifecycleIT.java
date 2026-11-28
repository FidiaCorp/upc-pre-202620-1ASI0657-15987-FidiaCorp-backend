package com.fidiacorp.credicasa.quotation.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.CreditOfferJpaRepository;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.ExportJobJpaRepository;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.OutboxEventJpaRepository;
import com.fidiacorp.credicasa.quotation.infrastructure.persistence.repository.QuotationVersionJpaRepository;
import com.fidiacorp.credicasa.quotation.testing.Downstreams;
import com.fidiacorp.credicasa.quotation.testing.Downstreams.Actor;
import com.fidiacorp.credicasa.shared.events.CreditOfferIssuedEvent;
import com.fidiacorp.credicasa.shared.events.QuotationVersionIssuedEvent;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** US10, US11, US12, US21, US22 de extremo a extremo en el microservicio con dobles HTTP reales (WireMock). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:quotation_lifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=15000")
@AutoConfigureMockMvc
class OfferLifecycleIT {
    @DynamicPropertySource
    static void downstreams(DynamicPropertyRegistry registry) {
        Downstreams.register(registry);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired CreditOfferJpaRepository offers;
    @Autowired QuotationVersionJpaRepository versions;
    @Autowired ExportJobJpaRepository exports;
    @Autowired OutboxEventJpaRepository outbox;
    @Autowired JdbcTemplate jdbc;
    Actor advisor;
    Actor client;
    Actor stranger;
    UUID propertyId;
    int status;

    @BeforeEach
    void prepare() {
        exports.deleteAll();
        outbox.deleteAll();
        versions.deleteAllInBatch();
        offers.deleteAll();
        Downstreams.reset();
        advisor = Downstreams.actor("advisor", "ROLE_REALTOR");
        client = Downstreams.actor("client", "ROLE_CLIENT");
        stranger = Downstreams.actor("stranger", "ROLE_CLIENT");
        Downstreams.account(client, "ROLE_CLIENT");
        Downstreams.account(advisor, "ROLE_REALTOR");
        propertyId = Downstreams.property(advisor, "250000.00", "PEN");
    }

    private MvcResult raw(MockHttpServletRequestBuilder request, Actor actor, Object body) throws Exception {
        if (actor != null) request.header("Authorization", "Bearer " + actor.token());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        MvcResult result = mvc.perform(request).andReturn();
        status = result.getResponse().getStatus();
        return result;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Actor actor, Object body) throws Exception {
        String text = raw(request, actor, body).getResponse().getContentAsString(StandardCharsets.UTF_8);
        return text.isBlank() ? json.nullNode() : json.readTree(text);
    }

    private Map<String, Object> offer(String email) {
        Map<String, Object> body = new HashMap<>();
        body.put("recipientEmail", email);
        body.put("propertyId", propertyId);
        body.put("bankCode", "BCP");
        body.put("loanAmount", "200000.00");
        body.put("currency", "PEN");
        body.put("annualInterestRate", "0.08512345");
        body.put("termMonths", 12);
        body.put("startDate", "2026-01-15");
        return body;
    }

    private String issue() throws Exception {
        JsonNode created = call(post("/api/v1/offers"), advisor, offer(client.email()));
        assertThat(status).as(created.toString()).isEqualTo(201);
        return created.get("id").asText();
    }

    @Test
    void us10IssuesOfferFreezesVersionOneAndRecordsTheEventInTheOutbox() throws Exception {
        String id = issue();
        assertThat(offers.count()).isEqualTo(1);
        var events = outbox.findAll();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).eventType).isEqualTo(CreditOfferIssuedEvent.TYPE);
        CreditOfferIssuedEvent event = json.readValue(events.get(0).payload, CreditOfferIssuedEvent.class);
        assertThat(event.recipientId()).isEqualTo(client.id());
        assertThat(event.notificationTitle()).isEqualTo("Oferta demostrativa de Banco de Crédito del Perú");
        assertThat(events.get(0).id).isEqualTo(event.eventId());
        JsonNode received = call(get("/api/v1/offers"), client, null);
        assertThat(received.get("totalElements").asInt()).isEqualTo(1);
        assertThat(call(get("/api/v1/offers/" + id), client, null).get("property").get("price").asText()).isEqualTo("250000.00");
        call(get("/api/v1/offers/" + id), advisor, null);
        assertThat(status).isEqualTo(404);
        JsonNode list = call(get("/api/v1/offers/" + id + "/versions"), client, null);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("versionNumber").asInt()).isEqualTo(1);
        assertThat(list.get(0).get("intact").asBoolean()).isTrue();
        assertThat(list.get(0).get("expired").asBoolean()).isFalse();
        assertThat(list.get(0).has("snapshot") && !list.get(0).get("snapshot").isNull()).isFalse();
    }

    @Test
    void us10RejectsInvalidRecipientsPropertiesAndTerms() throws Exception {
        call(post("/api/v1/offers"), advisor, offer("nobody@example.com"));
        assertThat(status).isEqualTo(404);
        Downstreams.account(stranger, "ROLE_REALTOR");
        call(post("/api/v1/offers"), advisor, offer(stranger.email()));
        assertThat(status).isEqualTo(404);
        Actor other = Downstreams.actor("other-advisor", "ROLE_REALTOR");
        call(post("/api/v1/offers"), other, offer(client.email()));
        assertThat(status).isEqualTo(403);
        Map<String, Object> usd = offer(client.email());
        usd.put("currency", "USD");
        assertThat(call(post("/api/v1/offers"), advisor, usd).get("detail").asText()).contains("moneda");
        assertThat(status).isEqualTo(400);
        Map<String, Object> tooMuch = offer(client.email());
        tooMuch.put("loanAmount", "250000.01");
        call(post("/api/v1/offers"), advisor, tooMuch);
        assertThat(status).isEqualTo(400);
        Map<String, Object> missingProperty = offer(client.email());
        missingProperty.put("propertyId", UUID.randomUUID());
        call(post("/api/v1/offers"), advisor, missingProperty);
        assertThat(status).isEqualTo(404);
        Map<String, Object> rate = offer(client.email());
        rate.put("annualInterestRate", "0.99");
        assertThat(call(post("/api/v1/offers"), advisor, rate).get("detail").asText()).contains("Tasa inválida");
        assertThat(status).isEqualTo(400);
        call(post("/api/v1/offers"), client, offer(client.email()));
        assertThat(status).isEqualTo(403);
        assertThat(offers.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void us10PricingOutageReturns503AndPersistsNothing() throws Exception {
        Downstreams.pricingDown();
        JsonNode problem = call(post("/api/v1/offers"), advisor, offer(client.email()));
        assertThat(status).isEqualTo(503);
        assertThat(problem.get("type").asText()).isEqualTo("https://api.credicasa.pe/problems/service-unavailable");
        assertThat(offers.count()).isZero();
        assertThat(versions.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void us11RecipientGeneratesTheScheduleOnceThroughPricing() throws Exception {
        String id = issue();
        call(get("/api/v1/offers/" + id + "/schedule"), client, null);
        assertThat(status).isEqualTo(404);
        JsonNode first = call(post("/api/v1/offers/" + id + "/schedule"), client, null);
        assertThat(status).isEqualTo(200);
        assertThat(first.get("quotationId").asText()).isEqualTo(Downstreams.V1_SIMULATION);
        assertThat(first.get("schedule")).hasSize(12);
        assertThat(first.get("schedule").get(11).get("finalBalance").asText()).isEqualTo("0.00");
        JsonNode second = call(post("/api/v1/offers/" + id + "/schedule"), client, null);
        assertThat(second).isEqualTo(first);
        assertThat(call(get("/api/v1/offers/" + id + "/schedule"), client, null)).isEqualTo(first);
        assertThat(offers.findById(UUID.fromString(id)).orElseThrow().simulationId).isEqualTo(UUID.fromString(Downstreams.V1_SIMULATION));
        // 2 cálculos: versión 1 (emisor) + cronograma del destinatario; el segundo POST reutiliza la simulación.
        assertThat(Downstreams.SERVER.findAll(com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/api/v1/simulations/calculate")))).hasSize(2);
        for (Actor denied : new Actor[]{advisor, stranger}) {
            call(post("/api/v1/offers/" + id + "/schedule"), denied, null);
            assertThat(status).isEqualTo(404);
        }
    }

    @Test
    void us21IssuerCreatesImmutableVersionsThatParticipantsCanVerify() throws Exception {
        String id = issue();
        JsonNode v2 = call(post("/api/v1/offers/" + id + "/versions"), advisor,
                Map.of("loanAmount", "190000.00", "annualInterestRate", "0.0825", "validityDays", 7));
        assertThat(status).as(v2.toString()).isEqualTo(201);
        assertThat(v2.get("versionNumber").asInt()).isEqualTo(2);
        assertThat(v2.get("annualInterestRate").asText()).isEqualTo("0.08250000");
        assertThat(v2.get("termMonths").asInt()).isEqualTo(12);
        String snapshot = versions.findAll().stream().filter(v -> v.versionNumber == 2).findFirst().orElseThrow().snapshotJson;
        assertThat(v2.get("snapshotHash").asText()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(snapshot.getBytes(StandardCharsets.UTF_8))));
        assertThat(v2.get("snapshot").get("schedule").get("quotationId").asText()).isEqualTo(Downstreams.V2_SIMULATION);
        assertThat(java.time.Duration.between(java.time.Instant.parse(v2.get("issuedAt").asText()),
                java.time.Instant.parse(v2.get("expiresAt").asText())).toDays()).isEqualTo(7);
        JsonNode list = call(get("/api/v1/offers/" + id + "/versions"), client, null);
        assertThat(list).hasSize(2);
        assertThat(list.get(0).get("versionNumber").asInt()).isEqualTo(2);
        assertThat(call(get("/api/v1/offers/" + id + "/versions/1"), advisor, null).get("snapshot").get("terms").get("annualInterestRate").asText())
                .isEqualTo("0.08512345");
        assertThat(call(get("/api/v1/quotations/versions/" + v2.get("id").asText() + "/verify"), client, null).get("intact").asBoolean()).isTrue();
        var event = outbox.findAll().stream().filter(e -> e.eventType.equals(QuotationVersionIssuedEvent.TYPE)).findFirst().orElseThrow();
        assertThat(json.readValue(event.payload, QuotationVersionIssuedEvent.class).versionNumber()).isEqualTo(2);
        call(post("/api/v1/offers/" + id + "/versions"), client, Map.of("annualInterestRate", "0.01"));
        assertThat(status).isEqualTo(403);
        call(post("/api/v1/offers/" + id + "/versions"), Downstreams.actor("x", "ROLE_REALTOR"), Map.of());
        assertThat(status).isEqualTo(404);
        call(post("/api/v1/offers/" + id + "/versions"), advisor, Map.of("loanAmount", "999999.00"));
        assertThat(status).isEqualTo(400);
        call(get("/api/v1/offers/" + id + "/versions"), stranger, null);
        assertThat(status).isEqualTo(404);
        call(get("/api/v1/offers/" + id + "/versions/9"), client, null);
        assertThat(status).isEqualTo(404);
        call(get("/api/v1/quotations/versions/" + v2.get("id").asText() + "/verify"), stranger, null);
        assertThat(status).isEqualTo(404);
        JsonNode inherited = call(post("/api/v1/offers/" + id + "/versions"), advisor, null);
        assertThat(status).isEqualTo(201);
        assertThat(inherited.get("versionNumber").asInt()).isEqualTo(3);
        assertThat(inherited.get("annualInterestRate").asText()).isEqualTo("0.08250000");
    }

    @Test
    void us22PdfAndXlsxComeFromTheSameSnapshotAndAreRecorded() throws Exception {
        String id = issue();
        MvcResult pdf = raw(get("/api/v1/offers/" + id + "/schedule/export/pdf"), client, null);
        assertThat(status).isEqualTo(200);
        assertThat(pdf.getResponse().getContentType()).isEqualTo("application/pdf");
        String snapshotHash = pdf.getResponse().getHeader("X-Snapshot-Sha256");
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        try (var document = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains(snapshotHash).contains("Versión 1").contains("0.00");
        }
        MvcResult xlsx = raw(get("/api/v1/offers/" + id + "/schedule/export/xlsx"), advisor, null);
        assertThat(status).isEqualTo(200);
        assertThat(xlsx.getResponse().getHeader("X-Snapshot-Sha256")).isEqualTo(snapshotHash);
        assertThat(new String(xlsx.getResponse().getContentAsByteArray(), 0, 2, StandardCharsets.US_ASCII)).isEqualTo("PK");
        MvcResult again = raw(get("/api/v1/offers/" + id + "/schedule/export/xlsx").param("version", "1"), client, null);
        assertThat(again.getResponse().getHeader("X-Content-Sha256")).isEqualTo(xlsx.getResponse().getHeader("X-Content-Sha256"));
        assertThat(again.getResponse().getHeader(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION)).contains("_v1.xlsx");
        JsonNode jobs = call(get("/api/v1/offers/" + id + "/exports"), client, null);
        assertThat(jobs).hasSize(3);
        assertThat(jobs.findValuesAsText("status")).containsOnly("COMPLETED");
        assertThat(jobs.findValuesAsText("snapshotHash")).containsOnly(snapshotHash);
        raw(get("/api/v1/offers/" + id + "/schedule/export/pdf"), stranger, null);
        assertThat(status).isEqualTo(404);
        raw(get("/api/v1/offers/" + id + "/schedule/export/pdf").param("version", "5"), client, null);
        assertThat(status).isEqualTo(404);
        call(get("/api/v1/offers/" + id + "/exports"), stranger, null);
        assertThat(status).isEqualTo(404);
    }

    @Test
    void us22TamperedSnapshotIsDetectedAndTheFailedExportIsRecorded() throws Exception {
        String id = issue();
        jdbc.update("update quotation_versions set snapshot_json = replace(snapshot_json, '200000.00', '100000.00') where offer_id = ?", UUID.fromString(id));
        assertThat(call(get("/api/v1/offers/" + id + "/versions"), client, null).get(0).get("intact").asBoolean()).isFalse();
        JsonNode problem = call(get("/api/v1/offers/" + id + "/schedule/export/pdf"), client, null);
        assertThat(status).isEqualTo(409);
        assertThat(problem.get("detail").asText()).contains("SHA-256");
        JsonNode jobs = call(get("/api/v1/offers/" + id + "/exports"), client, null);
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).get("status").asText()).isEqualTo("FAILED");
    }
}
