package com.fidiacorp.credicasa.application.dto.request;
import com.fidiacorp.credicasa.domain.model.Currency;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
public record CreatePropertyRequest(
    @NotBlank @Size(max=120) String title,
    @NotBlank @Size(max=250) String address,
    @Size(max=2000) String description,
    @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal price,
    @NotNull Currency currency,
    @Size(max=20) List<@NotBlank @Size(max=40) String> tags
) {
    public CreatePropertyRequest {
        title=title==null?null:title.strip();address=address==null?null:address.strip();
        description=description==null?"":description.strip();
    }
}
