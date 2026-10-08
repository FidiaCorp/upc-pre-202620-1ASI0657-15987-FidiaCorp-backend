package com.fidiacorp.credicasa.domain.ports.in;

import com.fidiacorp.credicasa.domain.model.Quotation;

public interface CalculateScheduleUseCase {
    Quotation calculateAndIssue(CalculateScheduleCommand command);
}
