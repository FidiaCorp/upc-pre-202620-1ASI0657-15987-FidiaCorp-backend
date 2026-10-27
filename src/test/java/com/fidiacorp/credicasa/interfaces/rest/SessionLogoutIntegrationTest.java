package com.fidiacorp.credicasa.interfaces.rest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.application.usecase.TokenRevocationService;
import com.fidiacorp.credicasa.domain.ports.out.TokenRevocationRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.RevokedTokenJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.*;
import com.fidiacorp.credicasa.infrastructure.security.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:logout;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.jwt.secret=test-signing-key-minimum-length-is-thirty-two-bytes"
})
@AutoConfigureMockMvc
class SessionLogoutIntegrationTest {
    private static final String PASSWORD="CorrectHorseBattery123!",EMAIL="session@example.com";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired RevokedTokenJpaRepository tokens;
    @Autowired TokenRevocationService service;
    @Autowired TokenRevocationRepositoryPort port;
    @Autowired JwtTokenProvider jwt;
    @Autowired CustomUserDetailsService users;
    @Autowired TokenRevocationCleanup cleanup;
    private UUID id;
    private String token;
    private String body(Object data)throws Exception{return json.writeValueAsString(data);}
    @BeforeEach void prepare()throws Exception {
        tokens.deleteAll();accounts.deleteAll();
        var result=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Lucía","lastName","Pérez","email",EMAIL,"password",PASSWORD))))
            .andExpect(status().isCreated()).andReturn();
        id=UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
        token=login();
    }
    private String login()throws Exception {
        var result=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email",EMAIL,"password",PASSWORD)))).andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    @Test void logsOutOnlyPresentedTokenAndKeepsOtherAndNewSessionsUsable()throws Exception {
        String other=login();assertThat(other).isNotEqualTo(token);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("token",other))))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+other)).andExpect(status().isOk());
        String newToken=login();assertThat(newToken).isNotEqualTo(token).isNotEqualTo(other);
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+newToken)).andExpect(status().isOk());
        assertThat(tokens.count()).isEqualTo(1);
    }
    @Test void storesOnlyHashAndExpiryAndFreshServiceReadsPersistentRevocation()throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        var stored=tokens.findAll().getFirst();
        String expected=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        assertThat(stored.tokenHash).isEqualTo(expected).hasSize(64).doesNotContain(token);
        assertThat(stored.expiresAt).isEqualTo(jwt.getExpirationFromToken(token));
        assertThat(new TokenRevocationService(port,Clock.systemUTC()).isRevoked(token)).isTrue();
    }
    @Test void requiresAuthenticatedBearerAndRejectsInvalidExpiredTokens()throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        String expired=new JwtTokenProvider("test-signing-key-minimum-length-is-thirty-two-bytes",-1000)
            .generateToken(users.loadUserByUsername(EMAIL));
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+expired)).andExpect(status().isUnauthorized());
        assertThat(tokens.count()).isZero();
    }
    @Test void assignsUniqueJtiEvenWhenGeneratingImmediately() {
        var user=users.loadUserByUsername(EMAIL);
        Set<String> generated=new HashSet<>(),identifiers=new HashSet<>();
        for(int i=0;i<20;i++) {
            String value=jwt.generateToken(user);generated.add(value);
            try {
                String payload=new String(Base64.getUrlDecoder().decode(value.split("\\.")[1]),StandardCharsets.UTF_8);
                identifiers.add(json.readTree(payload).get("jti").asText());
            } catch(Exception ex) {throw new AssertionError(ex);}
        }
        assertThat(generated).hasSize(20);assertThat(identifiers).hasSize(20);
    }
    @Test void repeatedConcurrentLogoutDoesNotFailOrDuplicateRecords()throws Exception {
        CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> logout=() -> {ready.countDown();go.await();service.revoke(id,token,jwt.getExpirationFromToken(token));return true;};
            Future<Boolean> first=executor.submit(logout),second=executor.submit(logout);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(first.get(10,TimeUnit.SECONDS)).isTrue();assertThat(second.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(tokens.count()).isEqualTo(1);assertThat(service.isRevoked(token)).isTrue();
    }
    @Test void scheduledCleanupDeletesOnlyExpiredEntries()throws Exception {
        mvc.perform(post("/api/v1/auth/logout").header("Authorization","Bearer "+token)).andExpect(status().isNoContent());
        tokens.saveAndFlush(new RevokedTokenJpaEntity("0".repeat(64),Instant.now().minusSeconds(1)));
        assertThat(tokens.count()).isEqualTo(2);
        cleanup.purgeExpired();
        assertThat(tokens.count()).isEqualTo(1);assertThat(service.isRevoked(token)).isTrue();
    }
    @Test void corsAllowsAuthorizedRequestsAndExposesRetryAfter()throws Exception {
        mvc.perform(options("/api/v1/auth/logout").header("Origin","http://localhost:5173")
            .header("Access-Control-Request-Method","POST").header("Access-Control-Request-Headers","Authorization,Content-Type"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:5173"))
            .andExpect(header().string("Access-Control-Allow-Headers",containsString("Authorization")));
        mvc.perform(get("/api/v1/users/me").header("Origin","http://localhost:5173").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Expose-Headers",containsString("Retry-After")));
    }
}
