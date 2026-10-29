package com.fidiacorp.credicasa.interfaces.rest;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Checks the generated contract over HTTP, not an authored JSON fixture. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.address=127.0.0.1",
    "spring.datasource.url=jdbc:h2:mem:openapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","spring.jpa.hibernate.ddl-auto=create-drop"
})
class OpenApiContractTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    private JsonNode doc;
    @BeforeEach void loadGeneratedContract()throws Exception {
        http.getRestTemplate().setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
        var response=http.getForEntity("/v3/api-docs",String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        doc=json.readTree(response.getBody());
        Path export=Path.of("target/openapi/generated.json");Files.createDirectories(export.getParent());
        Files.writeString(export,json.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
    }
    @Test void healthAndConfiguredCorsAreAvailableWithoutExposingDetails() throws Exception {
        var health=http.getForEntity("/actuator/health",String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(health.getBody()).path("status").asText()).isEqualTo("UP");
        assertThat(json.readTree(health.getBody()).has("components")).isFalse();
        HttpHeaders headers=new HttpHeaders();headers.setOrigin("http://localhost:5173");
        headers.setAccessControlRequestMethod(HttpMethod.GET);
        headers.setAccessControlRequestHeaders(List.of("Authorization"));
        var allowed=http.exchange("/api/v1/users/me",HttpMethod.OPTIONS,new HttpEntity<>(headers),String.class);
        assertThat(allowed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.getHeaders().getAccessControlAllowOrigin()).isEqualTo("http://localhost:5173");
        assertThat(allowed.getHeaders().getAccessControlExposeHeaders()).contains("Retry-After");
        assertThat(allowed.getHeaders().getAccessControlAllowCredentials()).isFalse();
        headers.setOrigin("https://untrusted.example");
        var denied=http.exchange("/api/v1/users/me",HttpMethod.OPTIONS,new HttpEntity<>(headers),String.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(denied.getHeaders().getAccessControlAllowOrigin()).isNull();
    }
    private JsonNode operation(String path,String method){return doc.path("paths").path("/api/v1"+path).path(method);}
    private JsonNode schema(String name){return doc.path("components").path("schemas").path(name);}
    @Test void documentsEveryOperationWithCorrectPublicAndPrivateSecurity() {
        Set<String> publicOperations=Set.of("post /api/v1/auth/login","post /api/v1/auth/register","get /api/v1/banking/benchmarks");
        int operations=0;
        for(var paths=doc.path("paths").fields();paths.hasNext();) {
            var path=paths.next();for(var methods=path.getValue().fields();methods.hasNext();) {
                var method=methods.next();if(!Set.of("get","post","put","patch","delete").contains(method.getKey()))continue;
                operations++;JsonNode op=method.getValue();String key=method.getKey()+" "+path.getKey();
                assertThat(op.path("summary").asText()).as(key).isNotBlank();assertThat(op.path("description").asText()).as(key).isNotBlank();
                if(publicOperations.contains(key))assertThat(op.path("security")).as(key).isEmpty();
                else assertThat(op.path("security").get(0).has("BearerAuth")).as(key).isTrue();
            }
        }
        assertThat(operations).isEqualTo(24);assertThat(doc.path("security").isMissingNode() || doc.path("security").isEmpty()).isTrue();
        assertThat(doc.path("info").has("contact")).isFalse();assertThat(doc.path("info").has("license")).isFalse();
        assertThat(doc.path("info").path("description").asText()).contains("DEMO").contains("simulados").doesNotContain("sub-100");
    }
    @Test void moneyAndRateResponsesArePlainStringsWhileCountsAndLegacyBankingKeepTheirTypes() {
        Map<String,List<String>> fields=Map.of(
            "FinancialMetricsDto",List.of("tea","tem","monthlyTir","tcea","van","totalInterest","totalPrincipal","totalLifeInsurance","totalPropertyInsurance","totalAdminFee","totalCost","discountRateUsed"),
            "InstallmentDto",List.of("initialBalance","interest","principalAmortization","installmentAmount","lifeInsurance","propertyInsurance","adminFee","totalPayment","finalBalance"),
            "SimulationResponseDto",List.of("loanAmount","propertyValue","estimatedMonthlyInstallment"),
            "PropertyResponse",List.of("price"),"CreditOfferResponse",List.of("loanAmount","annualInterestRate"),"OfferPropertySnapshotResponse",List.of("price"));
        fields.forEach((model,names)->names.forEach(name->{
            JsonNode property=schema(model).path("properties").path(name);
            assertThat(property.path("type").asText()).as(model+"."+name).isEqualTo("string");
            assertThat(property.path("pattern").asText()).contains("[0-9]");
            if(property.has("example"))assertThat(property.get("example").isTextual()).isTrue();
        }));
        assertThat(schema("SimulationResponseDto").path("properties").path("termMonths").path("type").asText()).isEqualTo("integer");
        assertThat(schema("PropertyPageResponse").path("properties").path("totalElements").path("type").asText()).isEqualTo("integer");
        assertThat(schema("BankRateResponseDto").path("properties").path("minTea").path("type").asText()).isEqualTo("number");
        assertThat(schema("InstallmentDto").path("properties").path("dueDate").path("format").asText()).isEqualTo("date");
        assertThat(schema("SimulationRequestDto").path("properties").path("annualInterestRate").path("oneOf")).hasSize(2);
        assertThat(schema("CreatePropertyRequest").path("properties").path("price").path("oneOf")).hasSize(2);
    }
    @Test void securityFailuresDoNotPromiseJsonAndTotpThrottlingDocumentsItsHeader() {
        for(String path:List.of("/auth/logout","/users/me","/properties","/offers","/notifications")) {
            String method=path.equals("/auth/logout")?"post":"get";
            JsonNode unauthorized=operation(path,method).path("responses").path("401");
            assertThat(unauthorized.isMissingNode()).isFalse();assertThat(unauthorized.has("content")).as(path).isFalse();
        }
        for(String path:List.of("/auth/login","/users/me/two-factor/setup","/users/me/two-factor/confirm","/users/me/two-factor/disable")) {
            JsonNode throttled=operation(path,"post").path("responses").path("429");
            assertThat(throttled.path("headers").has("Retry-After")).as(path).isTrue();
            assertThat(throttled.path("content").path("application/json").path("schema").path("$ref").asText()).endsWith("TwoFactorErrorResponse");
        }
        assertThat(operation("/auth/login","post").path("responses").path("401").path("content").path("application/json").path("schema").path("anyOf")).hasSize(2);
        assertThat(operation("/auth/logout","post").path("responses").path("204").has("content")).isFalse();
        for(String path:List.of("/properties/{id}","/offers/{offerId}","/offers/{offerId}/schedule","/simulations/{id}"))
            assertThat(operation(path,"get").path("responses").has("400")).as(path).isTrue();
        for(String path:List.of("/users/me/two-factor/setup","/users/me/two-factor/confirm","/users/me/two-factor/disable"))
            assertThat(operation(path,"post").path("responses").has("503")).as(path).isTrue();
        assertThat(operation("/auth/register","post").path("responses").path("201").path("headers").has("Location")).isFalse();
        assertThat(operation("/simulations/calculate","post").path("responses").path("201").path("headers").has("Location")).isFalse();
    }
    @Test void resourceDescriptionsExposeOwnershipDemoAndIdempotenceWithoutInventedClaims() {
        assertThat(operation("/offers/{offerId}","get").path("description").asText()).contains("destinatario").contains("404");
        assertThat(operation("/offers/{offerId}/schedule","post").path("description").asText()).contains("idempotencia").contains("snapshot").contains("DEMO");
        assertThat(operation("/offers/{offerId}/schedule","post").path("responses").has("200")).isTrue();
        assertThat(operation("/simulations/{id}","get").path("description").asText()).contains("heredada sin owner");
        assertThat(operation("/properties","get").path("description").asText()).contains("AND");
        assertThat(operation("/notifications","get").path("description").asText()).contains("5 segundos");
        assertThat(operation("/banking/benchmarks","get").path("description").asText()).contains("simulados").doesNotContain("garantizar");
        assertThat(operation("/banking/banks/{bankCode}/offer","get").path("responses").has("404")).isFalse();
        checkLocalReferences(doc);
    }
    private void checkLocalReferences(JsonNode node) {
        if(node.isObject()) {
            if(node.has("$ref")) {
                String ref=node.get("$ref").asText();if(ref.startsWith("#/"))assertThat(doc.at(ref.substring(1)).isMissingNode()).as(ref).isFalse();
            }
            node.elements().forEachRemaining(this::checkLocalReferences);
        } else if(node.isArray())node.elements().forEachRemaining(this::checkLocalReferences);
    }
    @Test void documentedStringsAndCreatedHeadersMatchTheActualWireRepresentation()throws Exception {
        String email="contract-"+UUID.randomUUID()+"@example.com",password="ContractPassword2026!";
        var registered=http.postForEntity("/api/v1/auth/register",Map.of("firstName","Ana","lastName","Pérez","email",email,"password",password),String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);assertThat(registered.getHeaders().getLocation()).isNull();
        var login=http.postForEntity("/api/v1/auth/login",Map.of("email",email,"password",password),String.class);
        String token=json.readTree(login.getBody()).get("token").asText();
        HttpHeaders headers=new HttpHeaders();headers.setBearerAuth(token);headers.setContentType(MediaType.APPLICATION_JSON);
        var property=http.exchange("/api/v1/properties",HttpMethod.POST,new HttpEntity<>(Map.of("title","Contrato","address","Dirección de prueba",
            "price","9999999999999999.99","currency","PEN"),headers),String.class);
        assertThat(property.getStatusCode()).isEqualTo(HttpStatus.CREATED);assertThat(property.getHeaders().getLocation()).isNotNull();
        assertThat(json.readTree(property.getBody()).get("price").asText()).isEqualTo("9999999999999999.99");
        var calculated=http.exchange("/api/v1/simulations/calculate",HttpMethod.POST,new HttpEntity<>(Map.of("loanAmount","1000.00","currency","PEN",
            "annualInterestRate","0.00000001","termMonths",2,"startDate","2024-01-31","discountRateAnnual","0"),headers),String.class);
        assertThat(calculated.getStatusCode()).isEqualTo(HttpStatus.CREATED);assertThat(calculated.getHeaders().getLocation()).isNull();
        JsonNode wire=json.readTree(calculated.getBody());
        assertThat(wire.get("loanAmount").isTextual()).isTrue();assertThat(wire.get("metrics").get("tea").isTextual()).isTrue();
        assertThat(wire.get("schedule").get(0).get("dueDate").asText()).isEqualTo("2024-02-29");
        assertThat(wire.get("metrics").get("tea").asText()).isEqualTo("0.00000001");
        assertThat(wire.toString()).doesNotContain("E-").doesNotContain("E+");
    }
    @Test void documentedPublicAndPrivatePathsBehaveAccordinglyOverRealHttp() {
        assertThat(http.getForEntity("/api/v1/banking/benchmarks",String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(http.getForEntity("/api/v1/users/me",String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/api/v1/auth/login",Map.of("email","missing@example.com","password","not-a-real-password"),String.class).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(http.postForEntity("/api/v1/auth/logout",null,String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
