package com.fidiacorp.credicasa.communication.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inbox_events")
public class InboxEventJpaEntity {
    @Id @Column(name = "event_id") public UUID eventId;
    @Column(name = "event_type", nullable = false, length = 100) public String eventType;
    @Column(name = "received_at", nullable = false) public Instant receivedAt;
}
