package com.fidiacorp.credicasa.communication.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class UserBlock {
    private final UUID id;
    private final UUID blockerId;
    private final UUID blockedId;
    private final String reason;
    private final LocalDateTime createdAt;

    public UserBlock(UUID id, UUID blockerId, UUID blockedId, String reason, LocalDateTime createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.blockerId = Objects.requireNonNull(blockerId, "blockerId must not be null");
        this.blockedId = Objects.requireNonNull(blockedId, "blockedId must not be null");
        this.reason = reason != null ? reason : "Usuario bloqueado por preferencias de privacidad";
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    public UUID getId() { return id; }
    public UUID getBlockerId() { return blockerId; }
    public UUID getBlockedId() { return blockedId; }
    public String getReason() { return reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
