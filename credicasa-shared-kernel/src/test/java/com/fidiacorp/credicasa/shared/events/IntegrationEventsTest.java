package com.fidiacorp.credicasa.shared.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationEventsTest {
    static final String HASH = "a".repeat(64);
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void creditOfferEventRoundTripsThroughJsonByType() throws Exception {
        var event = new CreditOfferIssuedEvent(UUID.randomUUID(), Instant.parse("2026-11-24T10:00:00Z"), "c-1", UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(), "BCP", "Nueva oferta BCP", new BigDecimal("150000.00"), "PEN");
        assertThat(event.eventType()).isEqualTo("credit.offer.issued.v1");
        Object back = mapper.readValue(mapper.writeValueAsString(event), EventTopology.payloadType(event.eventType()));
        assertThat(back).isEqualTo(event);
    }

    @Test
    void quotationVersionEventRoundTripsThroughJsonByType() throws Exception {
        var event = new QuotationVersionIssuedEvent(UUID.randomUUID(), Instant.now(), "c-2", UUID.randomUUID(), UUID.randomUUID(), 2,
                UUID.randomUUID(), HASH, "Versión 2 de su oferta");
        assertThat(event.eventType()).isEqualTo("quotation.version.issued.v1");
        assertThat(mapper.readValue(mapper.writeValueAsString(event), EventTopology.payloadType(event.eventType()))).isEqualTo(event);
    }

    @Test
    void rejectsInvalidPayloadsAndUnknownTypes() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new CreditOfferIssuedEvent(null, null, null, id, id, id, null, "t", null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreditOfferIssuedEvent(id, null, null, null, id, id, null, "t", null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreditOfferIssuedEvent(id, null, null, id, null, id, null, "t", null, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CreditOfferIssuedEvent(id, null, null, id, id, id, null, " ", null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CreditOfferIssuedEvent(id, null, null, id, id, id, null, null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(null, null, null, id, id, 1, id, HASH, "t")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, null, id, 1, id, HASH, "t")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, id, null, 1, id, HASH, "t")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, id, id, 1, null, HASH, "t")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, id, id, 0, id, HASH, "t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, id, id, 1, id, null, "t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, id, id, 1, id, "ABC", "t")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, id, id, 1, id, HASH, "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new QuotationVersionIssuedEvent(id, null, null, id, id, 1, id, HASH, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventTopology.payloadType("unknown.v9")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EventTopology.payloadType(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
