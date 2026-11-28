package com.fidiacorp.credicasa.communication.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.communication.application.dto.request.BlockUserRequest;
import com.fidiacorp.credicasa.communication.application.dto.request.SendMessageRequest;
import com.fidiacorp.credicasa.communication.testing.StubAccounts;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.repository.ChatMessageJpaRepository;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.repository.ConversationJpaRepository;
import com.fidiacorp.credicasa.communication.infrastructure.persistence.repository.UserBlockJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
    @Autowired StubAccounts accounts;
    @Autowired UserBlockJpaRepository blocks;
    @Autowired ChatMessageJpaRepository messages;
    @Autowired ConversationJpaRepository conversations;

    private String tokenA, tokenB, tokenC;
    private UUID userAId, userBId;

    private String registerAndLogin(String email, String firstName, String lastName) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("firstName", firstName, "lastName", lastName, "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());
        MvcResult loginRes = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions postJson(String path, String token, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private UUID startConversation(String token, UUID recipient) throws Exception {
        MvcResult result = postJson("/api/v1/messages/conversations", token, Map.of("recipientId", recipient)).andReturn();
        assertThat(result.getResponse().getStatus()).isIn(200, 201);
        return UUID.fromString(json.readTree(result.getResponse().getContentAsString()).get("conversationId").asText());
    }

    @BeforeEach
    void setUp() throws Exception {
        blocks.deleteAll();
        messages.deleteAll();
        conversations.deleteAll();
        accounts.deleteAll();
        tokenA = registerAndLogin("userA@fidiacorp.com", "Diego", "Pumahualcca");
        userAId = accounts.findByEmail("usera@fidiacorp.com").orElseThrow().id;
        tokenB = registerAndLogin("userB@fidiacorp.com", "Oliver", "Choque");
        userBId = accounts.findByEmail("userb@fidiacorp.com").orElseThrow().id;
        tokenC = registerAndLogin("userC@fidiacorp.com", "Intruso", "Externo");
    }

    @Test
    @DisplayName("US17: Bloquear a otro usuario impide envio de mensajes mutuos con 409 Conflict")
    void testBlockUserPreventsMessaging() throws Exception {
        UUID conversationId = startConversation(tokenB, userAId);

        postJson("/api/v1/messages/blocks/" + userBId, tokenA, new BlockUserRequest("Comportamiento spam"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockedId").value(userBId.toString()))
                .andExpect(jsonPath("$.blockId").isNotEmpty());

        mvc.perform(get("/api/v1/messages/blocks").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].blockedId").value(userBId.toString()));

        postJson("/api/v1/messages", tokenB, new SendMessageRequest(conversationId, userAId, "Hola, sigues ahi?"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.type").value("https://api.credicasa.pe/problems/conflict"));

        postJson("/api/v1/messages/conversations", tokenB, Map.of("recipientId", userAId)).andExpect(status().isConflict());

        // Re-blocking is idempotent and unblocking restores communication.
        postJson("/api/v1/messages/blocks/" + userBId, tokenA, new BlockUserRequest(null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("El usuario ya se encuentra bloqueado."));
        mvc.perform(delete("/api/v1/messages/blocks/" + userBId).header("Authorization", "Bearer " + tokenA)).andExpect(status().isNoContent());
        postJson("/api/v1/messages", tokenB, new SendMessageRequest(conversationId, null, "Gracias por desbloquear")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("US16/US18: Conversacion real con UUID, bandeja, lectura y exportacion .txt / .json")
    void testSendMessagesAndExportConversation() throws Exception {
        UUID conversationId = startConversation(tokenA, userBId);
        assertThat(startConversation(tokenB, userAId)).as("la misma pareja reutiliza la conversación").isEqualTo(conversationId);

        postJson("/api/v1/messages", tokenA, new SendMessageRequest(conversationId, userBId, "Hola Oliver, ¿sigue disponible el departamento?"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientId").value(userBId.toString()));
        postJson("/api/v1/messages", tokenB, new SendMessageRequest(conversationId, null, "¡Hola Diego! Sí, coordinemos una visita para el sábado."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientId").value(userAId.toString()));

        mvc.perform(get("/api/v1/messages/conversations").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].conversationId").value(conversationId.toString()))
                .andExpect(jsonPath("$[0].counterpartId").value(userAId.toString()))
                .andExpect(jsonPath("$[0].counterpartName").value("Diego Pumahualcca"))
                .andExpect(jsonPath("$[0].messageCount").value(2));

        mvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/messages").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].senderId").value(userAId.toString()));

        MvcResult textExport = mvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/export").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().exists("Content-Disposition"))
                .andReturn();
        String transcript = textExport.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(transcript).contains("CREDICASA - HISTORIAL OFICIAL DE CONVERSACION DE MENSAJERIA")
                .contains("Hola Oliver, ¿sigue disponible el departamento?")
                .contains("¡Hola Diego! Sí, coordinemos una visita para el sábado.");

        mvc.perform(get("/api/v1/messages/conversations/" + conversationId + "/export/json").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMessages").value(2))
                .andExpect(jsonPath("$.messages", hasSize(2)));
    }

    @Test
    @DisplayName("US16/US18 (OWASP API1): un tercero no puede inyectar mensajes, leer ni exportar una conversación ajena")
    void outsidersCannotHijackOrExfiltrateConversations() throws Exception {
        UUID conversationId = startConversation(tokenA, userBId);
        postJson("/api/v1/messages", tokenA, new SendMessageRequest(conversationId, null, "Dato privado")).andExpect(status().isOk());

        postJson("/api/v1/messages", tokenC, new SendMessageRequest(conversationId, userAId, "inyección")).andExpect(status().isForbidden());
        for (String path : new String[]{"/messages", "/export", "/export/json"}) {
            mvc.perform(get("/api/v1/messages/conversations/" + conversationId + path).header("Authorization", "Bearer " + tokenC))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.detail").exists());
        }
        UUID unknown = UUID.randomUUID();
        mvc.perform(get("/api/v1/messages/conversations/" + unknown + "/export").header("Authorization", "Bearer " + tokenA)).andExpect(status().isNotFound());
        postJson("/api/v1/messages", tokenA, new SendMessageRequest(unknown, userBId, "x")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/messages/conversations").header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        assertThat(messages.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("US16: validaciones de inicio de conversación (destinatario inexistente, consigo mismo, inmueble inexistente, recipient incoherente)")
    void conversationValidation() throws Exception {
        postJson("/api/v1/messages/conversations", tokenA, Map.of("recipientId", UUID.randomUUID())).andExpect(status().isBadRequest());
        postJson("/api/v1/messages/conversations", tokenA, Map.of("recipientId", userAId)).andExpect(status().isBadRequest());
        postJson("/api/v1/messages/conversations", tokenA, Map.of("recipientId", userBId, "propertyId", UUID.randomUUID())).andExpect(status().isNotFound());
        Map<String, Object> missing = new HashMap<>();
        postJson("/api/v1/messages/conversations", tokenA, missing).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.recipientId").exists());
        UUID conversationId = startConversation(tokenA, userBId);
        postJson("/api/v1/messages", tokenA, new SendMessageRequest(conversationId, UUID.randomUUID(), "x")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/messages/blocks/" + userAId).header("Authorization", "Bearer " + tokenA)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/messages/blocks/" + UUID.randomUUID()).header("Authorization", "Bearer " + tokenA)).andExpect(status().isBadRequest());
    }
}
