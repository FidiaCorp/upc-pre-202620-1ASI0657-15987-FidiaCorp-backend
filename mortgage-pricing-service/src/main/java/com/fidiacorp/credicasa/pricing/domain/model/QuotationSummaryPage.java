package com.fidiacorp.credicasa.pricing.domain.model;

import java.util.List;

public record QuotationSummaryPage(List<QuotationSummary> items, long totalElements) {
    public QuotationSummaryPage {
        items = List.copyOf(items);
    }
}
