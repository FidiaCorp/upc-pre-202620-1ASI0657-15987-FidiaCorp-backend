-- Explicit DDL for manually managed installations; current deployments use Hibernate update.
CREATE TABLE IF NOT EXISTS credit_offers (
 id UUID PRIMARY KEY,
 recipient_id UUID NOT NULL REFERENCES accounts(id),
 issuer_id UUID NOT NULL REFERENCES accounts(id),
 bank_code VARCHAR(20) NOT NULL CHECK(bank_code IN ('BCP','INTERBANK')),
 origin VARCHAR(20) NOT NULL CHECK(origin = 'DEMO'),
 property_id UUID NOT NULL,
 property_title VARCHAR(120) NOT NULL,
 property_address VARCHAR(250) NOT NULL,
 property_price NUMERIC(18,2) NOT NULL,
 property_currency VARCHAR(10) NOT NULL,
 loan_amount NUMERIC(18,2) NOT NULL CHECK(loan_amount > 0),
 currency VARCHAR(10) NOT NULL,
 annual_interest_rate NUMERIC(10,8) NOT NULL CHECK(annual_interest_rate > 0 AND annual_interest_rate <= 1),
 term_months INTEGER NOT NULL CHECK(term_months BETWEEN 1 AND 360),
 start_date DATE NOT NULL,
 simulation_id UUID,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 version BIGINT
);
CREATE TABLE IF NOT EXISTS offer_notifications (
 id UUID PRIMARY KEY,
 offer_id UUID NOT NULL REFERENCES credit_offers(id),
 recipient_id UUID NOT NULL REFERENCES accounts(id),
 title VARCHAR(120) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 read_at TIMESTAMP WITH TIME ZONE,
 version BIGINT,
 CONSTRAINT uk_notification_offer UNIQUE(offer_id)
);
CREATE INDEX IF NOT EXISTS idx_offers_recipient_created ON credit_offers(recipient_id,created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_recipient_created ON offer_notifications(recipient_id,created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_unread ON offer_notifications(recipient_id,read_at);
