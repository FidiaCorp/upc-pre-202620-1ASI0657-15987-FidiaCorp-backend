package com.fidiacorp.credicasa.communication.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.communication.domain.ports.out.InboxRepositoryPort;
import com.fidiacorp.credicasa.communication.testing.Events;
import com.fidiacorp.credicasa.shared.events.EventTopology;
import com.fidiacorp.credicasa.shared.security.JwtTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US10 + ADR-003: consumidor idempotente (inbox_events), DLQ para eventos inválidos y bandeja privada por JWT. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:communication_inbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class NotificationInboxIT {
    @Autowired NotificationEventsListener listener;
    @Autowired InboxRepositoryPort inbox;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired List<Queue> queues;
    @Autowired List<Binding> bindings;
    @Value("${app.security.jwt.secret}") String secret;

    private String token(UUID id) {
        return new JwtTokenIssuer(secret, 60_000).issue(id, List.of("ROLE_CLIENT"), null, "Cliente");
    }

    private JsonNode inbox(UUID user) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/notifications").header("Authorization", "Bearer " + token(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void redeliveredEventsCreateExactlyOneNotificationPerEvent() throws Exception {
        UUID recipient = UUID.randomUUID();
        UUID offer = UUID.randomUUID();
        var issued = Events.message(Events.offerIssued(offer, recipient));
        listener.onEvent(issued);
        listener.onEvent(issued);
        listener.onEvent(Events.message(Events.versionIssued(offer, recipient, 2)));
        JsonNode page = inbox(recipient);
        assertThat(page.get("totalElements").asInt()).isEqualTo(2);
        assertThat(page.get("unreadCount").asInt()).isEqualTo(2);
        assertThat(page.get("items").findValuesAsText("title")).contains("Oferta demostrativa de Banco de Crédito del Perú",
                "Nueva versión 2 de su oferta Banco de Crédito del Perú");
        String id = page.get("items").get(0).get("id").asText();
        mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + token(UUID.randomUUID())))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + token(recipient)))
                .andExpect(status().isOk());
        assertThat(inbox(recipient).get("unreadCount").asInt()).isEqualTo(1);
        assertThat(inbox(UUID.randomUUID()).get("totalElements").asInt()).isZero();
    }

    @Test
    void invalidOrUnknownEventsAreRejectedToTheDeadLetterQueue() {
        long before = inbox.count();
        assertThatThrownBy(() -> listener.onEvent(Events.raw(EventTopology.CREDIT_OFFER_ISSUED, UUID.randomUUID().toString(),
                "{not-json".getBytes(StandardCharsets.UTF_8)))).isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThatThrownBy(() -> listener.onEvent(Events.raw("payment.captured.v9", UUID.randomUUID().toString(), "{}".getBytes())))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(inbox.count()).isEqualTo(before);
    }

    @Test
    void topologyDeclaresDurableQueueWithDeadLetteringForBothEvents() {
        Queue queue = queues.stream().filter(q -> q.getName().equals(EventTopology.NOTIFICATIONS_QUEUE)).findFirst().orElseThrow();
        assertThat(queue.isDurable()).isTrue();
        assertThat(queue.getArguments()).containsEntry("x-dead-letter-exchange", EventTopology.DEAD_LETTER_EXCHANGE);
        assertThat(queues).extracting(Queue::getName).contains(EventTopology.NOTIFICATIONS_DLQ);
        assertThat(bindings).extracting(Binding::getRoutingKey)
                .contains(EventTopology.CREDIT_OFFER_ISSUED, EventTopology.QUOTATION_VERSION_ISSUED, EventTopology.DEAD_LETTER_ROUTING_KEY);
    }
}
