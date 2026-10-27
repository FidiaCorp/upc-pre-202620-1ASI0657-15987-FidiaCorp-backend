package com.fidiacorp.credicasa.application.dto.request;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.OfferBank;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
public record CreateCreditOfferRequest(
    @NotBlank @Email @Size(max=254) String recipientEmail,
    @NotNull UUID propertyId,
    @NotNull OfferBank bankCode,
    @NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal loanAmount,
    @NotNull Currency currency,
    @NotNull @DecimalMin(value="0",inclusive=false) @DecimalMax("1") @Digits(integer=1,fraction=8) BigDecimal annualInterestRate,
    @NotNull @Min(1) @Max(360) Integer termMonths,
    @NotNull LocalDate startDate
) {
    public CreateCreditOfferRequest {recipientEmail=recipientEmail==null?null:recipientEmail.strip().toLowerCase(Locale.ROOT);}
}
