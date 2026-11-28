-- pricing_db (MS-3 mortgage-pricing-service): simulaciones (US11) y cuotas. owner_id/client_id/property_id son UUID
-- lógicos de otros contextos: SIN FKs físicas (ADR-001 / PA-05). Importes NUMERIC(38,2) y tasas NUMERIC(38,24).
CREATE TABLE quotations (
    id                        UUID PRIMARY KEY,
    owner_id                  UUID,
    property_id               UUID,
    client_id                 UUID,
    client_doc                VARCHAR(30),
    client_name               VARCHAR(201),
    client_email              VARCHAR(254),
    property_address          VARCHAR(250),
    loan_amount               NUMERIC(38,2) NOT NULL,
    currency                  VARCHAR(10) NOT NULL,
    term_months               INTEGER NOT NULL,
    annual_interest_rate      NUMERIC(38,24) NOT NULL,
    grace_period_type         VARCHAR(20) NOT NULL,
    grace_period_months       INTEGER NOT NULL,
    balloon_amount            NUMERIC(38,2),
    balloon_month             INTEGER,
    life_insurance_rate       NUMERIC(38,24),
    property_insurance_rate   NUMERIC(38,24),
    property_value            NUMERIC(38,2),
    monthly_admin_fee         NUMERIC(38,2),
    start_date                DATE NOT NULL,
    discount_rate_used        NUMERIC(38,24),
    tea                       NUMERIC(38,24) NOT NULL,
    tem                       NUMERIC(38,24) NOT NULL,
    monthly_tir               NUMERIC(60,24) NOT NULL,
    tcea                      NUMERIC(280,24) NOT NULL,
    van                       NUMERIC(38,2) NOT NULL,
    total_interest            NUMERIC(38,2) NOT NULL,
    total_cost                NUMERIC(38,2) NOT NULL,
    status                    VARCHAR(20) NOT NULL,
    created_at                TIMESTAMP NOT NULL
);
CREATE INDEX idx_quotations_owner_created ON quotations(owner_id, created_at DESC);

CREATE TABLE installments (
    id                     BIGSERIAL PRIMARY KEY,
    quotation_id           UUID NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
    installment_number     INTEGER NOT NULL,
    due_date               DATE NOT NULL,
    initial_balance        NUMERIC(38,2) NOT NULL,
    interest               NUMERIC(38,2) NOT NULL,
    principal_amortization NUMERIC(38,2) NOT NULL,
    installment_amount     NUMERIC(38,2) NOT NULL,
    life_insurance         NUMERIC(38,2) NOT NULL,
    property_insurance     NUMERIC(38,2) NOT NULL,
    admin_fee              NUMERIC(38,2) NOT NULL,
    total_payment          NUMERIC(38,2) NOT NULL,
    final_balance          NUMERIC(38,2) NOT NULL,
    grace_type             VARCHAR(20) NOT NULL,
    CONSTRAINT uk_installment_number UNIQUE (quotation_id, installment_number)
);
