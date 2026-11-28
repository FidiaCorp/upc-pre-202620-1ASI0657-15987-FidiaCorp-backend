package com.fidiacorp.credicasa.shared.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PlainDecimalSerializerTest {
    record Payload(@JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal rate) {}

    @Test
    void writesExactPlainStringsWithoutScientificNotation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.writeValueAsString(new Payload(new BigDecimal("1E-8")))).isEqualTo("{\"rate\":\"0.00000001\"}");
        assertThat(mapper.writeValueAsString(new Payload(new BigDecimal("150000.00")))).isEqualTo("{\"rate\":\"150000.00\"}");
    }
}
