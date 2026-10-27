package com.fidiacorp.credicasa.application.usecase;
import com.fidiacorp.credicasa.application.dto.response.*;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.out.OfferNotificationRepositoryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.UUID;
@Service
public class OfferNotificationService {
    private final OfferNotificationRepositoryPort repository;
    private final Clock clock;
    public OfferNotificationService(OfferNotificationRepositoryPort repository,Clock clock) {this.repository=repository;this.clock=clock;}
    @Transactional(readOnly=true)
    public NotificationPageResponse list(UUID recipientId,boolean unreadOnly,int page,int size) {
        if(page<0 || size<1 || size>100)throw new IllegalArgumentException("Paginación inválida.");
        NotificationPage found=repository.findForRecipient(recipientId,unreadOnly,page,size);
        return new NotificationPageResponse(found.items().stream().map(OfferNotificationResponse::from).toList(),page,size,
            found.totalElements(),(found.totalElements()+size-1)/size,found.unreadCount());
    }
    @Transactional
    public OfferNotificationResponse markRead(UUID recipientId,UUID id) {
        return OfferNotificationResponse.from(repository.markReadForRecipient(id,recipientId,clock.instant())
            .orElseThrow(OfferResourceNotFoundException::new));
    }
}
