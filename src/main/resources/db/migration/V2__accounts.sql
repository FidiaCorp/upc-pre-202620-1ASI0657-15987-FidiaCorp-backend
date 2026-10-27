-- Applied automatically by Hibernate ddl-auto=update in the current deployment.
-- Also available for deployments that manage schema changes explicitly.
CREATE TABLE IF NOT EXISTS accounts (
 id UUID PRIMARY KEY,
 first_name VARCHAR(100) NOT NULL,
 last_name VARCHAR(100) NOT NULL,
 email VARCHAR(254) NOT NULL,
 password_hash VARCHAR(100) NOT NULL,
 roles VARCHAR(100) NOT NULL,
 version BIGINT,
 CONSTRAINT uk_accounts_email UNIQUE (email)
);
