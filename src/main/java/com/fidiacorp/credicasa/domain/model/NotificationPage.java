package com.fidiacorp.credicasa.domain.model;
import java.util.List;
public record NotificationPage(List<OfferNotification> items,long totalElements,long unreadCount) {
    public NotificationPage {items=List.copyOf(items);}
}
