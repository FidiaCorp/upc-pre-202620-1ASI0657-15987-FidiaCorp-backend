package com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** US21: fila inmutable (Hibernate @Immutable + trigger en PostgreSQL que rechaza UPDATE/DELETE). */
@Entity
@Immutable
@Table(name = "quotation_versions", uniqueConstraints = @UniqueConstraint(name = "uk_quotation_version", columnNames = {"offer_id", "version_number"}))
public class QuotationVersionJpaEntity {
    @Id public UUID id;
    @Column(name = "offer_id", nullable = false) public UUID offerId;
    @Column(name = "version_number", nullable = false) public int versionNumber;
    @Column(name = "loan_amount", nullable = false, precision = 18, scale = 2) public BigDecimal loanAmount;
    @Column(name = "annual_interest_rate", nullable = false, precision = 10, scale = 8) public BigDecimal annualInterestRate;
    @Column(name = "term_months", nullable = false) public int termMonths;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "TEXT") public String snapshotJson;
    @Column(name = "snapshot_hash", nullable = false, length = 64) public String snapshotHash;
    @Column(name = "issued_by", nullable = false) public UUID issuedBy;
    @Column(name = "simulation_id") public UUID simulationId;
    @Column(name = "issued_at", nullable = false) public Instant issuedAt;
    @Column(name = "expires_at", nullable = false) public Instant expiresAt;
}
