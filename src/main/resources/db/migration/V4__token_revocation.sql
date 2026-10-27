-- Explicit DDL for manually managed installations; current deployments use Hibernate update.
CREATE TABLE IF NOT EXISTS revoked_tokens (
 token_hash VARCHAR(64) PRIMARY KEY,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_revoked_tokens_expires_at ON revoked_tokens(expires_at);
