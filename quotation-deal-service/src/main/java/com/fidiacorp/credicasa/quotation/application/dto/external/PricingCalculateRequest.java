package com.fidiacorp.credicasa.quotation.application.dto.external;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fidiacorp.credicasa.shared.json.PlainDecimalSerializer;
import com.fidiacorp.credicasa.shared.money.Currency;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Contrato de POST /api/v1/simulations/calculate: sin gracia, balón, seguros ni gastos; COK = TEA. */
public record PricingCalculateRequest(@JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal loanAmount,
                                      @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal propertyValue, Currency currency,
                                      @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal annualInterestRate, int termMonths,
                                      LocalDate startDate, String propertyAddress,
                                      @JsonSerialize(using = PlainDecimalSerializer.class) BigDecimal discountRateAnnual) {
}
