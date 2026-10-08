-- =============================================================================
-- CrediCasa Mortgage Calculation Core - Initial Schema (V1)
-- FidiaCorp FinTech / PropTech
-- =============================================================================

CREATE TABLE IF NOT EXISTS quotations (
    id UUID PRIMARY KEY,
    loan_amount NUMERIC(18, 2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    term_months INTEGER NOT NULL,
    annual_interest_rate NUMERIC(10, 6) NOT NULL,
    grace_period_type VARCHAR(20) NOT NULL,
    grace_period_months INTEGER NOT NULL DEFAULT 0,
    balloon_amount NUMERIC(18, 2) DEFAULT 0.00,
    balloon_month INTEGER,
    life_insurance_rate NUMERIC(10, 6) DEFAULT 0.000500,
    property_insurance_rate NUMERIC(10, 6) DEFAULT 0.000250,
    property_value NUMERIC(18, 2),
    monthly_admin_fee NUMERIC(18, 2) DEFAULT 10.00,
    start_date DATE NOT NULL,
    tea NUMERIC(10, 6) NOT NULL,
    tem NUMERIC(10, 6) NOT NULL,
    monthly_tir NUMERIC(12, 8) NOT NULL,
    tcea NUMERIC(10, 6) NOT NULL,
    van NUMERIC(18, 2) NOT NULL,
    total_interest NUMERIC(18, 2) NOT NULL,
    total_cost NUMERIC(18, 2) NOT NULL,
    client_doc VARCHAR(30),
    client_name VARCHAR(150),
    client_email VARCHAR(100),
    property_address VARCHAR(250),
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS installments (
    id BIGSERIAL PRIMARY KEY,
    quotation_id UUID NOT NULL REFERENCES quotations(id) ON DELETE CASCADE,
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    initial_balance NUMERIC(18, 2) NOT NULL,
    interest NUMERIC(18, 2) NOT NULL,
    principal_amortization NUMERIC(18, 2) NOT NULL,
    installment_amount NUMERIC(18, 2) NOT NULL,
    life_insurance NUMERIC(18, 2) NOT NULL,
    property_insurance NUMERIC(18, 2) NOT NULL,
    admin_fee NUMERIC(18, 2) NOT NULL,
    total_payment NUMERIC(18, 2) NOT NULL,
    final_balance NUMERIC(18, 2) NOT NULL,
    grace_type VARCHAR(20) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_quotations_created_at ON quotations(created_at);
CREATE INDEX IF NOT EXISTS idx_installments_quotation_id ON installments(quotation_id);
CREATE INDEX IF NOT EXISTS idx_installments_number ON installments(quotation_id, installment_number);
