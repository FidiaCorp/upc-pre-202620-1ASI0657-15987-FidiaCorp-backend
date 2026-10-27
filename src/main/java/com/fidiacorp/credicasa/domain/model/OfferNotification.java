package com.fidiacorp.credicasa.domain.model;
import java.time.Instant;
import java.util.UUID;
public record OfferNotification(UUID id,UUID offerId,UUID recipientId,String title,Instant createdAt,Instant readAt) {}
