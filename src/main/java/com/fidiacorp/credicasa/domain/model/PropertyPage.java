package com.fidiacorp.credicasa.domain.model;
import java.util.List;
public record PropertyPage(List<Property> items, long totalElements) {
    public PropertyPage { items=List.copyOf(items); }
}
