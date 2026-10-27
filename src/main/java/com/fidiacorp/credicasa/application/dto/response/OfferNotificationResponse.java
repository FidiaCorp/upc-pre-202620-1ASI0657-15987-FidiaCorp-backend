package com.fidiacorp.credicasa.application.dto.response;
import com.fidiacorp.credicasa.domain.model.OfferNotification;
import java.time.Instant;
import java.util.UUID;
public record OfferNotificationResponse(UUID id,UUID offerId,String type,String origin,String title,Instant createdAt,Instant readAt) {
    public static OfferNotificationResponse from(OfferNotification n) {
        return new OfferNotificationResponse(n.id(),n.offerId(),"CREDIT_OFFER","DEMO",n.title(),n.createdAt(),n.readAt());
    }
}
