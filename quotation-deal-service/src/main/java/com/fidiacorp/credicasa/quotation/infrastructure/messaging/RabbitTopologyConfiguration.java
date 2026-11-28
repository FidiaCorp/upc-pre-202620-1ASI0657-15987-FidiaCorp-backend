package com.fidiacorp.credicasa.quotation.infrastructure.messaging;

import com.fidiacorp.credicasa.shared.events.EventTopology;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Productor: declara el topic exchange durable {@code credicasa.events} (las colas las declara el consumidor). */
@Configuration
public class RabbitTopologyConfiguration {
    @Bean
    public TopicExchange credicasaEventsExchange() {
        return new TopicExchange(EventTopology.EXCHANGE, true, false);
    }
}
