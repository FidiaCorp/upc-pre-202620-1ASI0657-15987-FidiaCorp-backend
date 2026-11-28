package com.fidiacorp.credicasa.communication.infrastructure.messaging;

import com.fidiacorp.credicasa.communication.application.usecase.IntegrationEventInbox;
import com.fidiacorp.credicasa.shared.events.EventTopology;
import com.fidiacorp.credicasa.shared.web.CorrelationId;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Listener RabbitMQ de {@code credicasa.notifications.queue}. Eventos malformados o desconocidos se rechazan sin
 * reencolar → dead-letter exchange → {@code credicasa.notifications.dlq}. Fallos transitorios (BD caída) se reintentan.
 */
@Component
public class NotificationEventsListener {
    private final IntegrationEventInbox inbox;

    public NotificationEventsListener(IntegrationEventInbox inbox) {
        this.inbox = inbox;
    }

    @RabbitListener(queues = EventTopology.NOTIFICATIONS_QUEUE, id = "notificationEvents")
    public void onEvent(Message message) {
        Object type = message.getMessageProperties().getHeader(EventTopology.HEADER_EVENT_TYPE);
        String eventType = type == null ? message.getMessageProperties().getReceivedRoutingKey() : type.toString();
        MDC.put(CorrelationId.MDC_KEY, "evt-" + message.getMessageProperties().getMessageId());
        try {
            inbox.handle(eventType, message.getBody());
        } catch (IntegrationEventInbox.InvalidEventException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
        }
    }
}
