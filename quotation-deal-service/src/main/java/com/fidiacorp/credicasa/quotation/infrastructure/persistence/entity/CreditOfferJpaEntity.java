package com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity;
import com.fidiacorp.credicasa.shared.money.Currency;
import com.fidiacorp.credicasa.quotation.domain.model.OfferBank;
import com.fidiacorp.credicasa.quotation.domain.model.OfferOrigin;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
@Entity
@Table(name="credit_offers",indexes=@Index(name="idx_offers_recipient_created",columnList="recipient_id,created_at"))
/** recipient_id, issuer_id y property_id son UUID lógicos de otros contextos (sin FK física). */
public class CreditOfferJpaEntity {
    @Id public UUID id;
    @Column(name="recipient_id",nullable=false) public UUID recipientId;
    @Column(name="issuer_id",nullable=false) public UUID issuerId;
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
