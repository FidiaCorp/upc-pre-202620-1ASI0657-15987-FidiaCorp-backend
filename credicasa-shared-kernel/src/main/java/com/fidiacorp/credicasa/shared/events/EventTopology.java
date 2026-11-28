package com.fidiacorp.credicasa.shared.events;

import java.util.Map;

/** Topología RabbitMQ compartida por productores (quotation-deal) y consumidores (communication-notification). */
public final class EventTopology {
    public static final String EXCHANGE = "credicasa.events";
    public static final String DEAD_LETTER_EXCHANGE = "credicasa.events.dlx";
    public static final String NOTIFICATIONS_QUEUE = "credicasa.notifications.queue";
    public static final String NOTIFICATIONS_DLQ = "credicasa.notifications.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "notifications.dead";
    public static final String CREDIT_OFFER_ISSUED = "credit.offer.issued.v1";
    public static final String QUOTATION_VERSION_ISSUED = "quotation.version.issued.v1";
    public static final String HEADER_EVENT_TYPE = "x-event-type";
    public static final String HEADER_EVENT_ID = "x-event-id";

    private static final Map<String, Class<? extends IntegrationEvent>> TYPES = Map.of(
            CREDIT_OFFER_ISSUED, CreditOfferIssuedEvent.class,
            QUOTATION_VERSION_ISSUED, QuotationVersionIssuedEvent.class);

    private EventTopology() {
    }

    public static Class<? extends IntegrationEvent> payloadType(String eventType) {
        Class<? extends IntegrationEvent> type = eventType == null ? null : TYPES.get(eventType);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
        return type;
    }
}
