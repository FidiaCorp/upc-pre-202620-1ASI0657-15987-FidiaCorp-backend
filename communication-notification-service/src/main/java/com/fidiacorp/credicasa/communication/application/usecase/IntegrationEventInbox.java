package com.fidiacorp.credicasa.communication.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fidiacorp.credicasa.communication.domain.model.InboxEntry;
import com.fidiacorp.credicasa.communication.domain.model.OfferNotification;
import com.fidiacorp.credicasa.communication.domain.ports.out.InboxRepositoryPort;
import com.fidiacorp.credicasa.communication.domain.ports.out.OfferNotificationRepositoryPort;
import com.fidiacorp.credicasa.shared.events.CreditOfferIssuedEvent;
import com.fidiacorp.credicasa.shared.events.EventTopology;
import com.fidiacorp.credicasa.shared.events.IntegrationEvent;
import com.fidiacorp.credicasa.shared.events.QuotationVersionIssuedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Clock;
import java.util.UUID;

/**
 * US10 asíncrono: consume credit.offer.issued.v1 y quotation.version.issued.v1 y crea el aviso privado del
 * destinatario. Idempotente: el {@code eventId} se registra en inbox_events en la MISMA transacción que el aviso,
 * así una redelivery de RabbitMQ no duplica notificaciones. Un payload inválido lanza {@link InvalidEventException}
 * (el listener lo envía a la DLQ sin reintentos).
 */
@Service
public class IntegrationEventInbox {
    private static final Logger log = LoggerFactory.getLogger(IntegrationEventInbox.class);
    private final InboxRepositoryPort inbox;
    private final OfferNotificationRepositoryPort notifications;
    private final ObjectMapper json;
    private final Clock clock;

    public static class InvalidEventException extends RuntimeException {
        public InvalidEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public IntegrationEventInbox(InboxRepositoryPort inbox, OfferNotificationRepositoryPort notifications, ObjectMapper json, Clock clock) {
        this.inbox = inbox;
        this.notifications = notifications;
        this.json = json;
        this.clock = clock;
    }

    /** @return true si se creó el aviso; false si el evento ya se había procesado. */
    @Transactional
    public boolean handle(String eventType, byte[] payload) {
        IntegrationEvent event = parse(eventType, payload);
        if (inbox.alreadyProcessed(event.eventId())) {
            log.info("[INBOX] Evento duplicado {} ({}) ignorado", event.eventId(), eventType);
            return false;
        }
        UUID offerId = event instanceof CreditOfferIssuedEvent offer ? offer.offerId() : ((QuotationVersionIssuedEvent) event).offerId();
        String title = event instanceof CreditOfferIssuedEvent offer ? offer.notificationTitle() : ((QuotationVersionIssuedEvent) event).notificationTitle();
        notifications.save(new OfferNotification(UUID.randomUUID(), offerId, event.recipientId(), title, clock.instant(), null));
        inbox.record(new InboxEntry(event.eventId(), eventType, clock.instant()));
        return true;
    }

    private IntegrationEvent parse(String eventType, byte[] payload) {
        try {
            return json.readValue(payload, EventTopology.payloadType(eventType));
        } catch (IOException | IllegalArgumentException e) {
            throw new InvalidEventException("Evento inválido (" + eventType + "): " + e.getMessage(), e);
        }
    }
}
