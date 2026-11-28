package com.fidiacorp.credicasa.communication.domain.ports.out;
import com.fidiacorp.credicasa.communication.domain.model.*;
import java.time.Instant;
import java.util.*;
public interface OfferNotificationRepositoryPort {
    OfferNotification save(OfferNotification notification);
    NotificationPage findForRecipient(UUID recipientId,boolean unreadOnly,int page,int size);
    Optional<OfferNotification> markReadForRecipient(UUID id,UUID recipientId,Instant readAt);
}
