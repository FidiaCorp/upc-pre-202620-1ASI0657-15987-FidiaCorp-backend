package com.fidiacorp.credicasa.interfaces.rest;
import com.fasterxml.jackson.databind.*;
import com.fidiacorp.credicasa.application.usecase.TwoFactorService;
import com.fidiacorp.credicasa.domain.model.TwoFactorException;
import com.fidiacorp.credicasa.domain.service.TotpAlgorithm;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:twofactor;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.two-factor.encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
@AutoConfigureMockMvc
@Import(TwoFactorIntegrationTest.TimeConfiguration.class)
class TwoFactorIntegrationTest {
    private static final String PASSWORD="CorrectHorseBattery123!",EMAIL="mfa@example.com",BASE="/api/v1/users/me/two-factor";
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock testClock() {return new MutableClock();}
    }
    static class MutableClock extends Clock {
        final AtomicReference<Instant> time=new AtomicReference<>(Instant.ofEpochSecond(1800000000L));
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return time.get();}
        void advance(long seconds){time.updateAndGet(t -> t.plusSeconds(seconds));}
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;
    @Autowired TotpAlgorithm totp;
    @Autowired AccountJpaRepository accounts;
    @Autowired TwoFactorJpaRepository states;
    @Autowired TwoFactorService service;
    private String token,secret;
    private UUID id;
    private String body(Object data)throws Exception{return json.writeValueAsString(data);}
    @BeforeEach void prepare()throws Exception {
        states.deleteAll();accounts.deleteAll();clock.time.set(Instant.ofEpochSecond(1800000000L));
        var response=mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Lucía","lastName","Pérez","email",EMAIL,"password",PASSWORD))))
            .andExpect(status().isCreated()).andReturn();
        id=UUID.fromString(json.readTree(response.getResponse().getContentAsString()).get("id").asText());
        var login=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email",EMAIL,"password",PASSWORD)))).andExpect(status().isOk()).andReturn();
        token=json.readTree(login.getResponse().getContentAsString()).get("token").asText();
    }
    private ResultActions action(String path,Object data)throws Exception {
        return mvc.perform(post(BASE+path).header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body(data)));
    }
    private ResultActions login(String otp)throws Exception {
        Map<String,String> data=new HashMap<>(Map.of("email",EMAIL,"password",PASSWORD));
        if(otp!=null)data.put("otp",otp);
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body(data)));
    }
    private String code(){return totp.code(secret,clock.instant().getEpochSecond()/30);}
    private String wrongCode(){return code().startsWith("0")?"1"+code().substring(1):"0"+code().substring(1);}
    private void setup()throws Exception {
        var result=action("/setup",Map.of("password",PASSWORD)).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("expiresIn").value(600)).andExpect(jsonPath("otpauthUri").exists()).andReturn();
        secret=json.readTree(result.getResponse().getContentAsString()).get("secret").asText();
    }
    private void enable()throws Exception{setup();action("/confirm",Map.of("otp",code())).andExpect(status().isOk());}
    @Test void gatesLoginAndDisablesOnlyWithFreshOtpAndPassword()throws Exception {
        enable();
        assertThat(states.findById(id).orElseThrow().encryptedSecret).doesNotContain(secret);
        mvc.perform(get(BASE).header("Authorization","Bearer "+token)).andExpect(status().isOk())
            .andExpect(jsonPath("enabled").value(true)).andExpect(jsonPath("secret").doesNotExist())
            .andExpect(jsonPath("encryptedSecret").doesNotExist());
        login(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("TWO_FACTOR_REQUIRED"))
            .andExpect(jsonPath("token").doesNotExist()).andExpect(jsonPath("roles").doesNotExist());
        login(code()).andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("TWO_FACTOR_INVALID"));
        clock.advance(30);login(code()).andExpect(status().isOk()).andExpect(jsonPath("token").isString());
        action("/disable",Map.of("password",PASSWORD,"otp",code())).andExpect(status().isUnauthorized());
        assertThat(states.findById(id).orElseThrow().enabled).isTrue();
        clock.advance(30);
        action("/disable",Map.of("password","wrong","otp",code())).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("code").value("INVALID_CREDENTIALS"));
        assertThat(states.findById(id).orElseThrow().enabled).isTrue();
        action("/disable",Map.of("password",PASSWORD,"otp",code())).andExpect(status().isOk())
            .andExpect(jsonPath("enabled").value(false));
        assertThat(states.findById(id).orElseThrow().encryptedSecret).isNull();login(null).andExpect(status().isOk());
    }
    @Test void pendingExpiresAndRegenerationCannotResetFailuresOrReplaceActiveSecret()throws Exception {
        setup();String old=secret;
        action("/confirm",Map.of("otp",wrongCode())).andExpect(status().isUnauthorized());
        setup();assertThat(secret).isNotEqualTo(old);
        assertThat(states.findById(id).orElseThrow().failedAttempts).isEqualTo(1);
        clock.advance(600);
        action("/confirm",Map.of("otp",code())).andExpect(status().isBadRequest())
            .andExpect(jsonPath("code").value("TWO_FACTOR_SETUP_EXPIRED"));
        assertThat(states.findById(id).orElseThrow().enabled).isFalse();
        mvc.perform(get(BASE).header("Authorization","Bearer "+token)).andExpect(jsonPath("pending").value(false));
        setup();action("/confirm",Map.of("otp",code())).andExpect(status().isOk());
        String encrypted=states.findById(id).orElseThrow().encryptedSecret;
        action("/setup",Map.of("password",PASSWORD)).andExpect(status().isConflict());
        assertThat(states.findById(id).orElseThrow().encryptedSecret).isEqualTo(encrypted);
    }
    @Test void persistsThrottleAcrossRequestsBlocksSetupAndResetsAfterWindowAndSuccess()throws Exception {
        enable();clock.advance(30);
        for(int i=0;i<4;i++) login(wrongCode()).andExpect(status().isUnauthorized());
        login(wrongCode()).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
        assertThat(states.findById(id).orElseThrow().failedAttempts).isEqualTo(5);
        login(null).andExpect(status().isTooManyRequests()).andExpect(jsonPath("token").doesNotExist());
        login(code()).andExpect(status().isTooManyRequests());
        action("/setup",Map.of("password",PASSWORD)).andExpect(status().isTooManyRequests());
        assertThat(states.findById(id).orElseThrow().failedAttempts).isEqualTo(5);
        clock.advance(900);login(code()).andExpect(status().isOk());
        assertThat(states.findById(id).orElseThrow().failedAttempts).isZero();
        assertThat(states.findById(id).orElseThrow().lockedUntil).isNull();
    }
    @Test void rejectsExpiredFutureAndMalformedOtpWithoutIssuingTokens()throws Exception {
        enable();String expired=code();clock.advance(30);
        login(expired).andExpect(status().isUnauthorized()).andExpect(jsonPath("token").doesNotExist());
        login(totp.code(secret,clock.instant().getEpochSecond()/30+1)).andExpect(status().isUnauthorized());
        login("abc").andExpect(status().isUnauthorized());
        login(code()).andExpect(status().isOk());
        assertThat(states.findById(id).orElseThrow().failedAttempts).isZero();
    }
    @Test void concurrentLoginConsumesOtpExactlyOnce()throws Exception {
        enable();clock.advance(30);String otp=code();CountDownLatch ready=new CountDownLatch(2),go=new CountDownLatch(1);
        try(ExecutorService executor=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt=() -> {ready.countDown();go.await();
                try{service.verifyLogin(id,otp);return true;}
                catch(TwoFactorException ex){assertThat(ex.code()).isEqualTo("TWO_FACTOR_INVALID");return false;}};
            Future<Boolean> first=executor.submit(attempt),second=executor.submit(attempt);
            assertThat(ready.await(5,TimeUnit.SECONDS)).isTrue();go.countDown();
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(true,false);
        }
    }
    @Test void protectsManagementAndFailsClosedOnCorruptStoredSecret()throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        action("/setup",Map.of("password","wrong")).andExpect(status().isUnauthorized());
        assertThat(states.findById(id).orElseThrow().encryptedSecret).isNull();
        enable();clock.advance(30);
        var entity=states.findById(id).orElseThrow();entity.encryptedSecret="v1:corrupt";states.saveAndFlush(entity);
        login(code()).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("code").value("TWO_FACTOR_UNAVAILABLE"))
            .andExpect(jsonPath("token").doesNotExist());
        assertThat(states.findById(id).orElseThrow().enabled).isTrue();
    }
}
