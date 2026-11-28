package com.fidiacorp.credicasa.communication.infrastructure.messaging;

import com.fidiacorp.credicasa.shared.events.EventTopology;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Consumidor: cola durable de avisos enlazada a ambos eventos, con dead-letter exchange y DLQ. */
@Configuration
public class NotificationTopologyConfiguration {
    @Bean
    public TopicExchange credicasaEventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(EventTopology.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(EventTopology.NOTIFICATIONS_QUEUE)
                .deadLetterExchange(EventTopology.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(EventTopology.DEAD_LETTER_ROUTING_KEY).build();
    }

    @Bean
    public Queue notificationsDeadLetterQueue() {
        return QueueBuilder.durable(EventTopology.NOTIFICATIONS_DLQ).build();
    }

    @Bean
    public Binding offerIssuedBinding() {
        return BindingBuilder.bind(notificationsQueue()).to(credicasaEventsExchange()).with(EventTopology.CREDIT_OFFER_ISSUED);
    }

    @Bean
    public Binding versionIssuedBinding() {
        return BindingBuilder.bind(notificationsQueue()).to(credicasaEventsExchange()).with(EventTopology.QUOTATION_VERSION_ISSUED);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(notificationsDeadLetterQueue()).to(deadLetterExchange()).with(EventTopology.DEAD_LETTER_ROUTING_KEY);
    }
}
