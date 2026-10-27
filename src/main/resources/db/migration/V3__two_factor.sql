-- Explicit DDL for manually managed installations. Current deployments use Hibernate update.
CREATE TABLE IF NOT EXISTS account_two_factor (
 account_id UUID PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
 enabled BOOLEAN NOT NULL DEFAULT FALSE,
 encrypted_secret VARCHAR(512),
 pending_expires_at TIMESTAMP WITH TIME ZONE,
 last_accepted_step BIGINT NOT NULL DEFAULT -1,
 failed_attempts INTEGER NOT NULL DEFAULT 0,
 locked_until TIMESTAMP WITH TIME ZONE,
 version BIGINT
);
