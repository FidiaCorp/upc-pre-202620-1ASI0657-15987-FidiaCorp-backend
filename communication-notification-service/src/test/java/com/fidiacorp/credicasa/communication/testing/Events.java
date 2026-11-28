package com.fidiacorp.credicasa.communication.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fidiacorp.credicasa.shared.events.CreditOfferIssuedEvent;
import com.fidiacorp.credicasa.shared.events.EventTopology;
import com.fidiacorp.credicasa.shared.events.IntegrationEvent;
import com.fidiacorp.credicasa.shared.events.QuotationVersionIssuedEvent;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Mensajes AMQP idénticos a los que publica el OutboxRelay de quotation-deal-service. */
public final class Events {
    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());

    private Events() {
    }

    public static CreditOfferIssuedEvent offerIssued(UUID offerId, UUID recipient) {
        return new CreditOfferIssuedEvent(UUID.randomUUID(), Instant.now(), "corr-test", offerId, recipient, UUID.randomUUID(), "BCP",
                "Oferta demostrativa de Banco de Crédito del Perú", new BigDecimal("200000.00"), "PEN");
    }

    public static QuotationVersionIssuedEvent versionIssued(UUID offerId, UUID recipient, int version) {
        return new QuotationVersionIssuedEvent(UUID.randomUUID(), Instant.now(), "corr-test", offerId, UUID.randomUUID(), version, recipient,
                "b".repeat(64), "Nueva versión " + version + " de su oferta Banco de Crédito del Perú");
    }

    public static Message message(IntegrationEvent event) {
        try {
            return raw(event.eventType(), event.eventId().toString(), JSON.writeValueAsBytes(event));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Message raw(String type, String id, byte[] body) {
        return MessageBuilder.withBody(body).setContentType("application/json").setMessageId(id)
                .setHeader(EventTopology.HEADER_EVENT_TYPE, type).build();
    }
}
