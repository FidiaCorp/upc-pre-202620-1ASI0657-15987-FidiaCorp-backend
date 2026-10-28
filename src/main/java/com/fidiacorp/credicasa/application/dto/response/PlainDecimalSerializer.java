package com.fidiacorp.credicasa.application.dto.response;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.math.BigDecimal;
public class PlainDecimalSerializer extends JsonSerializer<BigDecimal> {
    @Override public void serialize(BigDecimal value,JsonGenerator generator,SerializerProvider provider)throws IOException {
        generator.writeString(value.toPlainString());
    }
}
