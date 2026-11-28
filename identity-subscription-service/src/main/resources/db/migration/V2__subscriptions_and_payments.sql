-- US05/US06: suscripciones y pagos sandbox firmados (HMAC-SHA256); una referencia se usa una sola vez.
CREATE TABLE subscriptions (
    id          UUID PRIMARY KEY,
    account_id  UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    plan_name   VARCHAR(50) NOT NULL,
    status      VARCHAR(30) NOT NULL,
    start_date  DATE NOT NULL,
    end_date    DATE NOT NULL,
    auto_renew  BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_subscriptions_account_id ON subscriptions(account_id);
CREATE INDEX idx_subscriptions_status ON subscriptions(status);

CREATE TABLE payment_records (
    id                    UUID PRIMARY KEY,
    subscription_id       UUID NOT NULL REFERENCES subscriptions(id) ON DELETE CASCADE,
    account_id            UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    amount                NUMERIC(12,2) NOT NULL,
    currency              VARCHAR(10) NOT NULL,
    status                VARCHAR(30) NOT NULL,
    transaction_reference VARCHAR(100) NOT NULL UNIQUE,
    payment_method        VARCHAR(50) NOT NULL,
    processed_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_payments_subscription_id ON payment_records(subscription_id);
CREATE INDEX idx_payments_account_status ON payment_records(account_id, status);
