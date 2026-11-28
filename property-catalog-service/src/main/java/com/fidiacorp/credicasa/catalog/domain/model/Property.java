package com.fidiacorp.credicasa.catalog.domain.model;
import com.fidiacorp.credicasa.shared.money.Currency;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
/** Catalogue aggregate, independent of historical quotation snapshots. Legitimacy is the latest SUNARP report (nullable). */
public record Property(UUID id, UUID ownerId, String title, String address, String description,
        BigDecimal price, Currency currency, Set<String> tags, Instant createdAt, PropertyLegitimacyReport legitimacy) {
    public Property { tags=Collections.unmodifiableSortedSet(new TreeSet<>(tags)); }
    public Property(UUID id, UUID ownerId, String title, String address, String description,
            BigDecimal price, Currency currency, Set<String> tags, Instant createdAt) {
        this(id,ownerId,title,address,description,price,currency,tags,createdAt,null);
    }
    public boolean isOwnedBy(UUID accountId) { return ownerId.equals(accountId); }
}
