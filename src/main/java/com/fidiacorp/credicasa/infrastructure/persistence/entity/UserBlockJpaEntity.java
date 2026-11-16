package com.fidiacorp.credicasa.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "user_blocks", uniqueConstraints = @UniqueConstraint(name = "uk_user_blocks_pair", columnNames = {"blocker_id", "blocked_id"}))
public class UserBlockJpaEntity {

    @Id
    public UUID id;

    @Column(name = "blocker_id", nullable = false)
    public UUID blockerId;

    @Column(name = "blocked_id", nullable = false)
    public UUID blockedId;

    @Column(name = "reason", length = 250)
    public String reason;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt = LocalDateTime.now();

    public UserBlockJpaEntity() {}
}
