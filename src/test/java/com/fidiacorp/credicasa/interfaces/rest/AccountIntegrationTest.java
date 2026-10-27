package com.fidiacorp.credicasa.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import com.fidiacorp.credicasa.infrastructure.security.CustomUserDetailsService;
import com.fidiacorp.credicasa.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:accounts;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.security.jwt.secret=test-signing-key-minimum-length-is-thirty-two-bytes"
})
@AutoConfigureMockMvc
class AccountIntegrationTest {
    private static final String PASSWORD = "CorrectHorseBattery123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired PasswordEncoder encoder;
    @Autowired CustomUserDetailsService users;
    @Autowired JwtTokenProvider tokens;
    @BeforeEach void clean() { accounts.deleteAll(); }
    private String body(Object value) throws Exception { return json.writeValueAsString(value); }
    private void register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName"," Lucía ","lastName"," Pérez ","email",email,"password",PASSWORD))))
            .andExpect(status().isCreated()).andExpect(jsonPath("firstName").value("Lucía"))
            .andExpect(jsonPath("password").doesNotExist()).andExpect(jsonPath("passwordHash").doesNotExist())
            .andExpect(jsonPath("roles[0]").value("ROLE_CLIENT"));
    }
    private String login(String email) throws Exception {
        var result = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email",email,"password",PASSWORD))))
            .andExpect(status().isOk()).andExpect(jsonPath("type").value("Bearer"))
            .andExpect(jsonPath("email").value(email.toLowerCase()))
            .andExpect(jsonPath("expiresIn").isNumber()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }
    @Test void registrationLoginAndOwnProfileRemainPersistentAfterEmailChange() throws Exception {
        register("LUCIA@example.com");
        var persisted = accounts.findByEmail("lucia@example.com").orElseThrow();
        assertThat(persisted.passwordHash).isNotEqualTo(PASSWORD);
        assertThat(encoder.matches(PASSWORD,persisted.passwordHash)).isTrue();
        String token = login("lucia@example.com");
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("email").value("lucia@example.com"));
        mvc.perform(put("/api/v1/users/me").header("Authorization","Bearer "+token)
            .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("firstName","Luciana","lastName","Pérez","email","New@Example.com"))))
            .andExpect(status().isOk()).andExpect(jsonPath("email").value("new@example.com"));
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("firstName").value("Luciana"));
        assertThat(accounts.findByEmail("lucia@example.com")).isEmpty();
        assertThat(accounts.findByEmail("new@example.com").orElseThrow().id).isEqualTo(persisted.id);
        login("new@example.com");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email","lucia@example.com","password",PASSWORD)))).andExpect(status().isUnauthorized());
    }
    @Test void rejectsDuplicatesAndInvalidInputWithoutWriting() throws Exception {
        register("lucia@example.com");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Ana","lastName","Pérez","email"," LUCIA@EXAMPLE.COM ","password",PASSWORD))))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName"," ","lastName","","email","invalid","password","short"))))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("validationErrors.firstName").exists())
            .andExpect(jsonPath("validationErrors.email").exists()).andExpect(jsonPath("validationErrors.password").exists());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Ana","lastName","Pérez","email","ana@example.com","password","é".repeat(40)))))
            .andExpect(status().isBadRequest());
        assertThat(accounts.count()).isEqualTo(1);
    }
    @Test void profilesAreIsolatedAndRoleFieldsCannotEscalatePrivileges() throws Exception {
        register("one@example.com"); register("two@example.com");
        String token=login("one@example.com");
        mvc.perform(put("/api/v1/users/me").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Updated","lastName","Name","email","two@example.com"))))
            .andExpect(status().isConflict());
        mvc.perform(put("/api/v1/users/me").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName"," ","lastName","Name","email","one@example.com"))))
            .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/users/me").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Updated","lastName","Name","email","one@example.com","roles",new String[]{"ROLE_REALTOR"}))))
            .andExpect(status().isOk()).andExpect(jsonPath("roles[0]").value("ROLE_CLIENT"));
        assertThat(accounts.findByEmail("two@example.com").orElseThrow().firstName).isEqualTo("Lucía");
        assertThat(accounts.findByEmail("one@example.com").orElseThrow().roles).isEqualTo("ROLE_CLIENT");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("firstName","Ana","lastName","Pérez","email","three@example.com","password",PASSWORD,"roles",new String[]{"ROLE_REALTOR"}))))
            .andExpect(status().isCreated()).andExpect(jsonPath("roles[0]").value("ROLE_CLIENT"));
    }
    @Test void rejectsAnonymousInvalidExpiredDeletedUsersAndDemoCredentials() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer broken"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(body(Map.of("email","cliente@credicasa.pe","password","Cliente123!"))))
            .andExpect(status().isUnauthorized());
        register("one@example.com"); String token=login("one@example.com");
        String expired = new JwtTokenProvider("test-signing-key-minimum-length-is-thirty-two-bytes",-1000)
            .generateToken(users.loadUserByUsername("one@example.com"));
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+expired))
            .andExpect(status().isUnauthorized());
        accounts.deleteAll();
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer "+token))
            .andExpect(status().isUnauthorized());
    }
}
