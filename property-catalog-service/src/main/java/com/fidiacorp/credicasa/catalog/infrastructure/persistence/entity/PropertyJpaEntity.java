package com.fidiacorp.credicasa.catalog.infrastructure.persistence.entity;
import com.fidiacorp.credicasa.shared.money.Currency;
import jakarta.persistence.*;
import org.hibernate.annotations.BatchSize;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
@Entity
@Table(name="properties",indexes={@Index(name="idx_properties_owner",columnList="owner_id"),
    @Index(name="idx_properties_active_created",columnList="deleted_at,created_at")})
public class PropertyJpaEntity {
    @Id public UUID id;
    @Column(name="owner_id",nullable=false) public UUID ownerId;
    // owner_id es un UUID lógico de identity_db: sin FK física entre bounded contexts (ADR-001 / PA-05).
    @Column(nullable=false,length=120) public String title;
    @Column(nullable=false,length=250) public String address;
    @Column(nullable=false,length=2000) public String description;
    @Column(nullable=false,precision=18,scale=2) public BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) public Currency currency;
    @ElementCollection @BatchSize(size=100)
    @CollectionTable(name="property_tags",joinColumns=@JoinColumn(name="property_id"),
        uniqueConstraints=@UniqueConstraint(name="uk_property_tag",columnNames={"property_id","tag"}))
    @Column(name="tag",nullable=false,length=40) public Set<String> tags=new HashSet<>();
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Column(name="deleted_at") public Instant deletedAt;
    @Column(name="registry_number", length=50) public String registryNumber;
    @Column(name="registry_status", length=30) public String registryStatus = "PENDING_VERIFICATION";
    @Column(name="registry_verified_at") public Instant registryVerifiedAt;
    @Column(name="registry_office", length=100) public String registryOffice;
    @Column(name="registry_notes", length=1000) public String registryNotes;
    @Column(name="registry_owner_name", length=200) public String registryOwnerName;
    @Column(name="registry_certificate_hash", length=64) public String registryCertificateHash;
    @Version public Long version;
}
