package com.fidiacorp.credicasa.infrastructure.persistence.entity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name="account_two_factor")
public class TwoFactorJpaEntity {
    @Id @Column(name="account_id") public UUID accountId;
    @MapsId @OneToOne(fetch=FetchType.LAZY,optional=false)
    @JoinColumn(name="account_id") public AccountJpaEntity account;
    @Column(nullable=false) public boolean enabled;
    @Column(length=512) public String encryptedSecret;
    public Instant pendingExpiresAt;
    @Column(nullable=false) public long lastAcceptedStep=-1;
    @Column(nullable=false) public int failedAttempts;
    public Instant lockedUntil;
    @Version public Long version;
}
