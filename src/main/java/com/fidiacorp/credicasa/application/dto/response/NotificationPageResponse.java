package com.fidiacorp.credicasa.application.dto.response;
import java.util.List;
public record NotificationPageResponse(List<OfferNotificationResponse> items,int page,int size,
    long totalElements,long totalPages,long unreadCount) {}
