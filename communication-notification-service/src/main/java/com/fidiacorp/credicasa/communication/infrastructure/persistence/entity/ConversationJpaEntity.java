package com.fidiacorp.credicasa.communication.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversations_participant_a", columnList = "participant_a_id"),
        @Index(name = "idx_conversations_participant_b", columnList = "participant_b_id")})
public class ConversationJpaEntity {
    @Id public UUID id;
    @Column(name = "property_id") public UUID propertyId;
    @Column(name = "participant_a_id", nullable = false) public UUID participantAId;
    @Column(name = "participant_b_id", nullable = false) public UUID participantBId;
    @Column(name = "created_at", nullable = false) public Instant createdAt;
}
