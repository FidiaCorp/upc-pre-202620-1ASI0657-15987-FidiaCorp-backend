package com.fidiacorp.credicasa.pricing.domain.ports.in;

import com.fidiacorp.credicasa.pricing.domain.model.Quotation;

public interface CalculateScheduleUseCase {
    Quotation calculateAndIssue(CalculateScheduleCommand command);
}
