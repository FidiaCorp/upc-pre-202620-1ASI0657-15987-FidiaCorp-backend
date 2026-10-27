package com.fidiacorp.credicasa.domain.model;
import java.util.List;
public record CreditOfferPage(List<CreditOffer> items,long totalElements) {
    public CreditOfferPage {items=List.copyOf(items);}
}
