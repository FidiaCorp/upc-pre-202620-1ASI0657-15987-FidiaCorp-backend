-- quotation_db (MS-4 quotation-deal-service): ofertas DEMO (US10). recipient_id, issuer_id, property_id y simulation_id
-- son UUID lógicos de identity_db, property_db y pricing_db: SIN FKs físicas entre bounded contexts (ADR-001 / PA-05).
CREATE TABLE credit_offers (
    id                    UUID PRIMARY KEY,
    recipient_id          UUID NOT NULL,
    issuer_id             UUID NOT NULL,
    bank_code             VARCHAR(20) NOT NULL CHECK (bank_code IN ('BCP','INTERBANK')),
    origin                VARCHAR(20) NOT NULL CHECK (origin = 'DEMO'),
    property_id           UUID NOT NULL,
    property_title        VARCHAR(120) NOT NULL,
    property_address      VARCHAR(250) NOT NULL,
    property_price        NUMERIC(18,2) NOT NULL CHECK (property_price > 0),
    property_currency     VARCHAR(10) NOT NULL,
    loan_amount           NUMERIC(18,2) NOT NULL CHECK (loan_amount > 0),
    currency              VARCHAR(10) NOT NULL,
    annual_interest_rate  NUMERIC(10,8) NOT NULL CHECK (annual_interest_rate > 0 AND annual_interest_rate <= 1),
    term_months           INTEGER NOT NULL CHECK (term_months BETWEEN 1 AND 360),
    start_date            DATE NOT NULL,
    simulation_id         UUID,
    created_at            TIMESTAMP WITH TIME ZONE NOT NULL,
    version               BIGINT
);
CREATE INDEX idx_offers_recipient_created ON credit_offers(recipient_id, created_at);
CREATE INDEX idx_offers_issuer ON credit_offers(issuer_id);
