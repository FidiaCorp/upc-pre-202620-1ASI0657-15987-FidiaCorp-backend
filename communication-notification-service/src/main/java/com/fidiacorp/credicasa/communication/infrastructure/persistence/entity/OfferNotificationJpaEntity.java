package com.fidiacorp.credicasa.communication.infrastructure.persistence.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="offer_notifications",
    indexes={@Index(name="idx_notifications_recipient_created",columnList="recipient_id,created_at"),
        @Index(name="idx_notifications_unread",columnList="recipient_id,read_at")})
/** offer_id y recipient_id son UUID lógicos (quotation_db / identity_db): sin FK física. */
public class OfferNotificationJpaEntity {
    @Id public UUID id;
    @Column(name="offer_id",nullable=false) public UUID offerId;
    @Column(name="recipient_id",nullable=false) public UUID recipientId;
    @Column(nullable=false,length=120) public String title;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="read_at") public Instant readAt;
    @Version public Long version;
}
