package com.fidiacorp.credicasa.infrastructure.persistence;
import com.fidiacorp.credicasa.domain.model.*;
import com.fidiacorp.credicasa.domain.ports.out.OfferNotificationRepositoryPort;
import com.fidiacorp.credicasa.infrastructure.persistence.entity.OfferNotificationJpaEntity;
import com.fidiacorp.credicasa.infrastructure.persistence.repository.OfferNotificationJpaRepository;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.*;
@Repository
public class OfferNotificationRepositoryAdapter implements OfferNotificationRepositoryPort {
    private final OfferNotificationJpaRepository repository;
    public OfferNotificationRepositoryAdapter(OfferNotificationJpaRepository repository) {this.repository=repository;}
    public OfferNotification save(OfferNotification n) {
        OfferNotificationJpaEntity e=new OfferNotificationJpaEntity();e.id=n.id();e.offerId=n.offerId();e.recipientId=n.recipientId();
        e.title=n.title();e.createdAt=n.createdAt();e.readAt=n.readAt();return map(repository.saveAndFlush(e));
    }
    public NotificationPage findForRecipient(UUID recipient,boolean unreadOnly,int page,int size) {
        var paging=PageRequest.of(page,size,Sort.by(Sort.Order.desc("createdAt"),Sort.Order.desc("id")));
        var found=unreadOnly?repository.findByRecipientIdAndReadAtIsNull(recipient,paging):repository.findByRecipientId(recipient,paging);
        return new NotificationPage(found.getContent().stream().map(this::map).toList(),found.getTotalElements(),
            repository.countByRecipientIdAndReadAtIsNull(recipient));
    }
    public Optional<OfferNotification> markReadForRecipient(UUID id,UUID recipient,Instant at) {
        repository.markRead(id,recipient,at);return repository.findByIdAndRecipientId(id,recipient).map(this::map);
    }
    private OfferNotification map(OfferNotificationJpaEntity e) {
        return new OfferNotification(e.id,e.offerId,e.recipientId,e.title,e.createdAt,e.readAt);
    }
}
