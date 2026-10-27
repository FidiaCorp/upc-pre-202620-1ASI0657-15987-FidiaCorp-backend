package com.fidiacorp.credicasa.domain.model;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Catalogue aggregate, independent of historical quotation snapshots. */
public record Property(UUID id, UUID ownerId, String title, String address, String description,
        BigDecimal price, Currency currency, Set<String> tags, Instant createdAt) {
    public Property { tags=Collections.unmodifiableSortedSet(new TreeSet<>(tags)); }
}
