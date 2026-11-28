package com.fidiacorp.credicasa.communication.acceptance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.communication.infrastructure.messaging.NotificationEventsListener;
import com.fidiacorp.credicasa.communication.testing.Events;
import com.fidiacorp.credicasa.communication.testing.IdentityStubController;
import com.fidiacorp.credicasa.communication.testing.InMemoryDirectories;
import com.fidiacorp.credicasa.communication.testing.StubAccount;
import com.fidiacorp.credicasa.communication.testing.StubAccounts;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@CucumberContextConfiguration
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:communication_acceptance;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
public class CommunicationSteps {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired StubAccounts accounts;
    @Autowired IdentityStubController identity;
    @Autowired InMemoryDirectories directories;
    @Autowired NotificationEventsListener listener;
    StubAccount first;
    StubAccount second;
    String conversationId;
    int status;
    MvcResult raw;

    private StubAccount account(String first, String last) {
        StubAccount a = new StubAccount();
        a.id = UUID.randomUUID();
        a.firstName = first;
        a.lastName = last;
        a.email = a.id + "@example.com";
        a.password = "x";
        return accounts.saveAndFlush(a);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, StubAccount actor, Object body) throws Exception {
        request.header("Authorization", "Bearer " + identity.token(actor));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        raw = mvc.perform(request).andReturn();
        status = raw.getResponse().getStatus();
        String text = raw.getResponse().getContentAsString(StandardCharsets.UTF_8);
        return text.isBlank() || !(text.startsWith("{") || text.startsWith("[")) ? json.nullNode() : json.readTree(text);
    }

    @Given("two clients registered in the identity service")
    public void clients() {
        first = account("Ana", "Perez");
        second = account("Bruno", "Diaz");
    }

    @When("the event {string} for the first client is delivered twice")
    public void delivered(String type) {
        var message = Events.message(Events.offerIssued(UUID.randomUUID(), first.id));
        assertThat(message.getMessageProperties().getHeader("x-event-type").toString()).isEqualTo(type);
        listener.onEvent(message);
        listener.onEvent(message);
    }

    @Then("the first client has {int} unread notification titled {string}")
    public void unread(int count, String title) throws Exception {
        JsonNode page = call(get("/api/v1/notifications"), first, null);
        assertThat(page.get("unreadCount").asInt()).isEqualTo(count);
        assertThat(page.get("items").get(0).get("title").asText()).isEqualTo(title);
    }

    @Then("the first client has {int} unread notifications")
    public void unreadCount(int count) throws Exception {
        assertThat(call(get("/api/v1/notifications"), first, null).get("unreadCount").asInt()).isEqualTo(count);
    }

    @And("the second client has {int} notifications")
    public void none(int count) throws Exception {
        assertThat(call(get("/api/v1/notifications"), second, null).get("totalElements").asInt()).isEqualTo(count);
    }

    @And("the first client marks the notification as read")
    public void read() throws Exception {
        String id = call(get("/api/v1/notifications"), first, null).get("items").get(0).get("id").asText();
        assertThat(call(patch("/api/v1/notifications/" + id + "/read"), first, null).get("readAt").isNull()).isFalse();
    }

    @When("the first client contacts the second client about an active property")
    public void contact() throws Exception {
        JsonNode conversation = call(post("/api/v1/messages/conversations"), first, Map.of("recipientId", second.id,
                "propertyId", directories.activeProperty()));
        assertThat(status).isIn(200, 201);
        conversationId = conversation.get("conversationId").asText();
        assertThat(conversation.get("counterpartName").asText()).isEqualTo("Bruno Diaz");
    }

    @And("the first client sends the message {string}")
    public void send(String content) throws Exception {
        call(post("/api/v1/messages"), first, Map.of("conversationId", conversationId, "content", content));
        assertThat(status).isEqualTo(200);
    }

    @Then("the second client reads {int} message from {string}")
    public void readsMessage(int count, String sender) throws Exception {
        JsonNode messages = call(get("/api/v1/messages/conversations/" + conversationId + "/messages"), second, null);
        assertThat(messages).hasSize(count);
        assertThat(messages.get(0).get("senderName").asText()).isEqualTo(sender);
    }

    @And("a third user cannot read that conversation")
    public void third() throws Exception {
        call(get("/api/v1/messages/conversations/" + conversationId + "/messages"), account("Eve", "Spy"), null);
        assertThat(status).isEqualTo(403);
    }

    @And("the second client blocks the first client")
    public void block() throws Exception {
        call(post("/api/v1/messages/blocks/" + first.id), second, Map.of("reason", "Spam"));
        assertThat(status).isIn(200, 201);
    }

    @Then("the first client cannot send messages in that conversation")
    public void blocked() throws Exception {
        call(post("/api/v1/messages"), first, Map.of("conversationId", conversationId, "content", "¿Hola?"));
        assertThat(status).isEqualTo(409);
    }

    @Then("the first client exports the conversation as text and JSON with {int} message")
    public void export(int count) throws Exception {
        call(get("/api/v1/messages/conversations/" + conversationId + "/export"), first, null);
        assertThat(status).isEqualTo(200);
        assertThat(raw.getResponse().getContentType()).startsWith("text/plain");
        assertThat(raw.getResponse().getContentAsString(StandardCharsets.UTF_8)).contains("Hola, quisiera visitar el inmueble");
        JsonNode exported = call(get("/api/v1/messages/conversations/" + conversationId + "/export/json"), first, null);
        assertThat(exported.get("totalMessages").asInt()).isEqualTo(count);
    }
}
