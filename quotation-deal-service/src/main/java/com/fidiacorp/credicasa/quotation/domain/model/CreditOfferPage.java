package com.fidiacorp.credicasa.quotation.domain.model;
import java.util.List;
public record CreditOfferPage(List<CreditOffer> items,long totalElements) {
    public CreditOfferPage {items=List.copyOf(items);}
}
