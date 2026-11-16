package com.fidiacorp.credicasa.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.application.dto.request.BlockUserRequest;
import com.fidiacorp.credicasa.application.dto.request.SendMessageRequest;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.AccountJpaRepository;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.ChatMessageJpaRepository;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.UserBlockJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:messaging;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.security.jwt.secret=test-signing-key-minimum-length-is-thirty-two-bytes"
})
@AutoConfigureMockMvc
class MessagingIntegrationTest {

    private static final String PASSWORD = "CorrectHorseBattery123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AccountJpaRepository accounts;
    @Autowired UserBlockJpaRepository blocks;
    @Autowired ChatMessageJpaRepository messages;

    private String tokenA;
    private UUID userAId;

    private String tokenB;
    private UUID userBId;

    private String registerAndLogin(String email, String firstName, String lastName) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of(
                        "firstName", firstName,
                        "lastName", lastName,
                        "email", email,
                        "password", PASSWORD
                )))).andExpect(status().isCreated());

        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();

        return json.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();
    }

    @BeforeEach
    void setUp() throws Exception {
        blocks.deleteAll();
        messages.deleteAll();
        accounts.deleteAll();

        tokenA = registerAndLogin("userA@fidiacorp.com", "Diego", "Pumahualcca");
        userAId = accounts.findByEmail("usera@fidiacorp.com").orElseThrow().id;

        tokenB = registerAndLogin("userB@fidiacorp.com", "Oliver", "Choque");
        userBId = accounts.findByEmail("userb@fidiacorp.com").orElseThrow().id;
    }

    @Test
    @DisplayName("US17: Bloquear a otro usuario impide envio de mensajes mutuos")
    void testBlockUserPreventsMessaging() throws Exception {
        // User A blocks User B
        BlockUserRequest blockReq = new BlockUserRequest("Comportamiento spam");
        mvc.perform(post("/api/v1/messages/blocks/" + userBId)
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(blockReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedId").value(userBId.toString()));

        // Check blocked list for User A
        mvc.perform(get("/api/v1/messages/blocks")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].blockedId").value(userBId.toString()));

        // User B attempts to message User A -> rejected
        SendMessageRequest sendReq = new SendMessageRequest(UUID.randomUUID(), userAId, "Hola, sigues ahi?");
        mvc.perform(post("/api/v1/messages")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(sendReq)))
                .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("US18: Enviar mensajes y descargar historial de conversacion en archivo .txt y json")
    void testSendMessagesAndExportConversation() throws Exception {
        UUID conversationId = UUID.randomUUID();

        // Msg 1 from User A to User B
        SendMessageRequest msg1 = new SendMessageRequest(conversationId, userBId, "Hola Oliver, ¿sigue disponible el departamento?");
        mvc.perform(post("/api/v1/messages")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(msg1)))
                .andExpect(status().isOk());

        // Msg 2 from User B to User A
        SendMessageRequest msg2 = new SendMessageRequest(conversationId, userAId, "¡Hola Diego! Sí, coordinemos una visita para el sábado.");
        mvc.perform(post("/api/v1/messages")
                .header("Authorization", "Bearer " + tokenB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(msg2)))
                .andExpect(status().isOk());

        // Export as Text file
        MvcResult textExport = mvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/export")
                .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();

        String transcript = textExport.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(transcript).contains("CREDICASA - HISTORIAL OFICIAL DE CONVERSACION DE MENSAJERIA");
        assertThat(transcript).contains("Hola Oliver, ¿sigue disponible el departamento?");
        assertThat(transcript).contains("¡Hola Diego! Sí, coordinemos una visita para el sábado.");

        // Export as JSON
        mvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/export/json")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMessages").value(2))
                .andExpect(jsonPath("$.messages", hasSize(2)));
    }
}
