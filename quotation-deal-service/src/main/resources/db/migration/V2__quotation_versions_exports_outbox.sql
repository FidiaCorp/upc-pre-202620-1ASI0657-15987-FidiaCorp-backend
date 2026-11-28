-- US21: versiones inmutables (snapshot JSON canónico + SHA-256 + vigencia). FK solo dentro del contexto.
CREATE TABLE quotation_versions (
    id                    UUID PRIMARY KEY,
    offer_id              UUID NOT NULL REFERENCES credit_offers(id) ON DELETE RESTRICT,
    version_number        INTEGER NOT NULL CHECK (version_number >= 1),
    loan_amount           NUMERIC(18,2) NOT NULL,
    annual_interest_rate  NUMERIC(10,8) NOT NULL,
    term_months           INTEGER NOT NULL,
    snapshot_json         TEXT NOT NULL,
    snapshot_hash         VARCHAR(64) NOT NULL CHECK (snapshot_hash ~ '^[0-9a-f]{64}$'),
    issued_by             UUID NOT NULL,
    simulation_id         UUID,
    issued_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at            TIMESTAMP WITH TIME ZONE NOT NULL CHECK (expires_at > issued_at),
    CONSTRAINT uk_quotation_version UNIQUE (offer_id, version_number)
);

-- Inmutabilidad también a nivel de base de datos: ningún UPDATE ni DELETE sobre versiones emitidas.
CREATE FUNCTION reject_quotation_version_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'quotation_versions es inmutable (US21)';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_quotation_versions_immutable BEFORE UPDATE OR DELETE ON quotation_versions
    FOR EACH ROW EXECUTE FUNCTION reject_quotation_version_change();

-- US22: trazabilidad de exportaciones PDF/XLSX generadas desde el snapshot de una versión.
CREATE TABLE export_jobs (
    id              UUID PRIMARY KEY,
    offer_id        UUID NOT NULL REFERENCES credit_offers(id) ON DELETE RESTRICT,
    version_id      UUID NOT NULL REFERENCES quotation_versions(id) ON DELETE RESTRICT,
    version_number  INTEGER NOT NULL,
    format          VARCHAR(10) NOT NULL CHECK (format IN ('PDF','XLSX')),
    status          VARCHAR(20) NOT NULL CHECK (status IN ('COMPLETED','FAILED')),
    requested_by    UUID NOT NULL,
    file_name       VARCHAR(200),
    content_sha256  VARCHAR(64),
    size_bytes      BIGINT NOT NULL,
    snapshot_hash   VARCHAR(64) NOT NULL,
    requested_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at    TIMESTAMP WITH TIME ZONE,
    error_message   VARCHAR(500)
);
CREATE INDEX idx_export_jobs_offer ON export_jobs(offer_id, requested_at DESC);

-- ADR-003 Transactional Outbox → RabbitMQ (credicasa.events).
CREATE TABLE outbox_events (
    id            UUID PRIMARY KEY,
    aggregate_id  UUID NOT NULL,
    event_type    VARCHAR(100) NOT NULL,
    payload       TEXT NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE,
    attempts      INTEGER NOT NULL DEFAULT 0,
    last_error    VARCHAR(500)
);
CREATE INDEX idx_outbox_pending ON outbox_events(created_at) WHERE published_at IS NULL;
