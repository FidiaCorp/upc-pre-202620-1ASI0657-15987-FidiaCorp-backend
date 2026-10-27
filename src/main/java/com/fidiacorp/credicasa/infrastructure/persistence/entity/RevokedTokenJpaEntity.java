package com.fidiacorp.credicasa.infrastructure.persistence.entity;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="revoked_tokens",indexes=@Index(name="idx_revoked_tokens_expires_at",columnList="expires_at"))
public class RevokedTokenJpaEntity {
    @Id @Column(name="token_hash",length=64) public String tokenHash;
    @Column(name="expires_at",nullable=false) public Instant expiresAt;
    public RevokedTokenJpaEntity() {}
    public RevokedTokenJpaEntity(String tokenHash,Instant expiresAt) {this.tokenHash=tokenHash;this.expiresAt=expiresAt;}
}
