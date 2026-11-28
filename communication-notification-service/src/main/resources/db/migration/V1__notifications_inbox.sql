-- communication_db (MS-5 communication-notification-service). offer_id y recipient_id son UUID lógicos de
-- quotation_db e identity_db: SIN FKs físicas entre bounded contexts (ADR-001 / PA-05).
CREATE TABLE offer_notifications (
    id            UUID PRIMARY KEY,
    offer_id      UUID NOT NULL,
    recipient_id  UUID NOT NULL,
    title         VARCHAR(120) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at       TIMESTAMP WITH TIME ZONE,
    version       BIGINT
);
CREATE INDEX idx_notifications_recipient_created ON offer_notifications(recipient_id, created_at);
CREATE INDEX idx_notifications_unread ON offer_notifications(recipient_id, read_at);

-- ADR-003 Idempotent Inbox: un event_id de RabbitMQ se procesa una sola vez.
CREATE TABLE inbox_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    received_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
