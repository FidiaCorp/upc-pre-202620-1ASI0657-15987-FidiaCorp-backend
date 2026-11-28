-- identity_db (MS-1 identity-subscription-service): cuentas, segundo factor TOTP y revocación selectiva de JWT.
-- Solo FKs internas del bounded context Identity & Subscription; ningún otro contexto referencia físicamente estas tablas.
CREATE TABLE accounts (
    id            UUID PRIMARY KEY,
    first_name    VARCHAR(100) NOT NULL,
    last_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    roles         VARCHAR(100) NOT NULL,
    version       BIGINT,
    CONSTRAINT uk_accounts_email UNIQUE (email)
);

CREATE TABLE account_two_factor (
    account_id          UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    enabled             BOOLEAN NOT NULL DEFAULT FALSE,
    encrypted_secret    VARCHAR(512),
    pending_expires_at  TIMESTAMP WITH TIME ZONE,
    last_accepted_step  BIGINT NOT NULL DEFAULT -1,
    failed_attempts     INTEGER NOT NULL DEFAULT 0,
    locked_until        TIMESTAMP WITH TIME ZONE,
    version             BIGINT
);

CREATE TABLE revoked_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);
