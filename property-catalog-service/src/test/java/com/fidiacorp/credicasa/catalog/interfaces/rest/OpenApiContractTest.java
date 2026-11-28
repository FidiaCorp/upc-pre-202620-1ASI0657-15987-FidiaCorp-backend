package com.fidiacorp.credicasa.catalog.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato OpenAPI generado por property-catalog-service (exportado a target/openapi para el Swagger agregado del gateway). */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:catalog_openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
class OpenApiContractTest {
    static final int OPERATIONS = 7;
    static final Set<String> PUBLIC_OPERATIONS = Set.of();
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    JsonNode doc;

    @BeforeEach
    void load() throws Exception {
        http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        var response = http.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        doc = json.readTree(response.getBody());
        Path export = Path.of("target/openapi/property-catalog-service.json");
        Files.createDirectories(export.getParent());
        Files.writeString(export, json.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
    }

    @Test
    void healthIsUpWithoutDetailsAndNoCorsIsEmittedByTheMicroservice() throws Exception {
        var health = http.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(health.getBody()).path("status").asText()).isEqualTo("UP");
        assertThat(json.readTree(health.getBody()).has("components")).isFalse();
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:5173");
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        var preflight = http.exchange("/api/v1/x", HttpMethod.OPTIONS, new HttpEntity<>(headers), String.class);
        assertThat(preflight.getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    void documentsEveryOperationWithCorrectSecurityAndHidesInternalApi() {
        int operations = 0;
        for (var paths = doc.path("paths").fields(); paths.hasNext(); ) {
            var path = paths.next();
            assertThat(path.getKey()).as("API interna oculta").doesNotStartWith("/api/v1/internal");
            for (var methods = path.getValue().fields(); methods.hasNext(); ) {
                var method = methods.next();
                if (!Set.of("get", "post", "put", "patch", "delete").contains(method.getKey())) continue;
                operations++;
                JsonNode op = method.getValue();
                String key = method.getKey() + " " + path.getKey();
                assertThat(op.path("summary").asText()).as(key).isNotBlank();
                assertThat(op.path("description").asText()).as(key).isNotBlank();
                if (PUBLIC_OPERATIONS.contains(key)) assertThat(op.path("security")).as(key).isEmpty();
                else assertThat(op.path("security").get(0).has("BearerAuth")).as(key).isTrue();
                assertThat(op.path("responses").has("500")).as(key).isTrue();
            }
        }
        assertThat(operations).isEqualTo(OPERATIONS);
        assertThat(doc.path("info").path("title").asText()).contains("CrediCasa");
        assertThat(doc.path("components").path("securitySchemes").has("BearerAuth")).isTrue();
    }
}
