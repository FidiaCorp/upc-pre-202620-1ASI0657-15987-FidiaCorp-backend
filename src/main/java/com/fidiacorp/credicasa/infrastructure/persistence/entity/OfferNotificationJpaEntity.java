package com.fidiacorp.credicasa.infrastructure.persistence.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="offer_notifications",uniqueConstraints=@UniqueConstraint(name="uk_notification_offer",columnNames="offer_id"),
    indexes={@Index(name="idx_notifications_recipient_created",columnList="recipient_id,created_at"),
        @Index(name="idx_notifications_unread",columnList="recipient_id,read_at")})
public class OfferNotificationJpaEntity {
    @Id public UUID id;
    @Column(name="offer_id",nullable=false) public UUID offerId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="offer_id",insertable=false,updatable=false)
    public CreditOfferJpaEntity offer;
    @Column(name="recipient_id",nullable=false) public UUID recipientId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id",insertable=false,updatable=false)
    public AccountJpaEntity recipient;
    @Column(nullable=false,length=120) public String title;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="read_at") public Instant readAt;
    @Version public Long version;
}
