package com.fidiacorp.credicasa.infrastructure.persistence.entity;
import com.fidiacorp.credicasa.domain.model.Currency;
import com.fidiacorp.credicasa.domain.model.OfferBank;
import com.fidiacorp.credicasa.domain.model.OfferOrigin;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="credit_offers",indexes=@Index(name="idx_offers_recipient_created",columnList="recipient_id,created_at"))
public class CreditOfferJpaEntity {
    @Id public UUID id;
    @Column(name="recipient_id",nullable=false) public UUID recipientId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="recipient_id",insertable=false,updatable=false)
    public AccountJpaEntity recipient;
    @Column(name="issuer_id",nullable=false) public UUID issuerId;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="issuer_id",insertable=false,updatable=false)
    public AccountJpaEntity issuer;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public OfferBank bankCode;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public OfferOrigin origin;
    // Logical property ID only: the independent snapshot survives catalogue deletion.
    @Column(nullable=false) public UUID propertyId;
    @Column(nullable=false,length=120) public String propertyTitle;
    @Column(nullable=false,length=250) public String propertyAddress;
    @Column(nullable=false,precision=18,scale=2) public BigDecimal propertyPrice;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) public Currency propertyCurrency;
    @Column(nullable=false,precision=18,scale=2) public BigDecimal loanAmount;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) public Currency currency;
    @Column(nullable=false,precision=10,scale=8) public BigDecimal annualInterestRate;
    @Column(nullable=false) public int termMonths;
    @Column(nullable=false) public LocalDate startDate;
    public UUID simulationId;
    @Column(name="created_at",nullable=false) public Instant createdAt;
    @Version public Long version;
}
