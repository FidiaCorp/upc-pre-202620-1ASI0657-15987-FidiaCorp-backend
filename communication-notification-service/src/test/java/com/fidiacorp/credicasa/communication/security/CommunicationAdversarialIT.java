package com.fidiacorp.credicasa.communication.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.communication.testing.IdentityStubController;
import com.fidiacorp.credicasa.communication.testing.StubAccount;
import com.fidiacorp.credicasa.communication.testing.StubAccounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** QA-03 en mensajería: V12–V15 y V20 (hijacking de chat, exportación ajena, suplantación de remitente). */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:communication_adversarial;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class CommunicationAdversarialIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubAccounts accounts;
    @Autowired IdentityStubController identity;

    private StubAccount account(String name) {
        StubAccount a = new StubAccount();
        a.id = UUID.randomUUID();
        a.firstName = name;
        a.lastName = "Test";
        a.email = a.id + "@example.com";
        a.password = "x";
        return accounts.saveAndFlush(a);
    }

    @Test
    @DisplayName("QA-03 mensajería: terceros no leen, escriben ni exportan conversaciones ajenas")
    void adversarialVectorsAreRejected() throws Exception {
        StubAccount a = account("Ana");
        StubAccount b = account("Bruno");
        StubAccount eve = account("Eve");
        String conversation = json.readTree(mvc.perform(post("/api/v1/messages/conversations").header("Authorization", "Bearer " + identity.token(a))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("recipientId", b.id))))
                .andReturn().getResponse().getContentAsString()).get("conversationId").asText();
        mvc.perform(post("/api/v1/messages").header("Authorization", "Bearer " + identity.token(a)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("conversationId", conversation, "content", "dato-privado-123")))).andExpect(status().isOk());
        String evil = "Bearer " + identity.token(eve);
        mvc.perform(get("/api/v1/messages/conversations/" + conversation + "/messages").header("Authorization", evil))
                .andExpect(status().isForbidden()).andExpect(content().string(not(containsString("dato-privado-123"))));
        mvc.perform(get("/api/v1/messages/conversations/" + conversation + "/export").header("Authorization", evil)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/messages/conversations/" + conversation + "/export/json").header("Authorization", evil)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/messages").header("Authorization", evil).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("conversationId", conversation, "content", "inyección")))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/messages").header("Authorization", "Bearer " + identity.token(a)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("conversationId", conversation, "recipientId", eve.id, "content", "desvío"))))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/messages/conversations/" + UUID.randomUUID() + "/messages").header("Authorization", evil)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/messages/conversations").header("Authorization", evil).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("recipientId", UUID.randomUUID())))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }
}
