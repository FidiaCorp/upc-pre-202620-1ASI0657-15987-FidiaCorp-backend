package com.fidiacorp.credicasa.quotation.infrastructure.persistence.entity;

import com.fidiacorp.credicasa.quotation.domain.model.ExportFormat;
import com.fidiacorp.credicasa.quotation.domain.model.ExportStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
public class ExportJobJpaEntity {
    @Id public UUID id;
    @Column(name = "offer_id", nullable = false) public UUID offerId;
    @Column(name = "version_id", nullable = false) public UUID versionId;
    @Column(name = "version_number", nullable = false) public int versionNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) public ExportFormat format;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) public ExportStatus status;
    @Column(name = "requested_by", nullable = false) public UUID requestedBy;
    @Column(name = "file_name", length = 200) public String fileName;
    @Column(name = "content_sha256", length = 64) public String contentSha256;
    @Column(name = "size_bytes", nullable = false) public long sizeBytes;
    @Column(name = "snapshot_hash", nullable = false, length = 64) public String snapshotHash;
    @Column(name = "requested_at", nullable = false) public Instant requestedAt;
    @Column(name = "completed_at") public Instant completedAt;
    @Column(name = "error_message", length = 500) public String errorMessage;
}
