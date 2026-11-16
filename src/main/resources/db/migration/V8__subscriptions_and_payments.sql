-- V8: Subscriptions, payment records and property legal status tracking
CREATE TABLE IF NOT EXISTS subscriptions (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    plan_name VARCHAR(50) NOT NULL,
    status VARCHAR(30) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    auto_renew BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_subscriptions_account_id ON subscriptions(account_id);
CREATE INDEX IF NOT EXISTS idx_subscriptions_status ON subscriptions(status);

CREATE TABLE IF NOT EXISTS payment_records (
    id UUID PRIMARY KEY,
    subscription_id UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    account_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    amount NUMERIC(12,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    status VARCHAR(30) NOT NULL,
    transaction_reference VARCHAR(100) NOT NULL UNIQUE,
    payment_method VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payments_subscription_id ON payment_records(subscription_id);
CREATE INDEX IF NOT EXISTS idx_payments_reference ON payment_records(transaction_reference);

-- Extension of properties table for Sunarp registry legitimacy tracking (US09)
ALTER TABLE properties ADD COLUMN IF NOT EXISTS registry_number VARCHAR(50);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS registry_status VARCHAR(30) DEFAULT 'PENDING_VERIFICATION';
ALTER TABLE properties ADD COLUMN IF NOT EXISTS registry_verified_at TIMESTAMP;
ALTER TABLE properties ADD COLUMN IF NOT EXISTS registry_office VARCHAR(100);
ALTER TABLE properties ADD COLUMN IF NOT EXISTS registry_notes TEXT;
