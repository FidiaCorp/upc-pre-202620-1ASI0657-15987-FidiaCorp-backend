-- US16–US18: conversaciones privadas, mensajes y bloqueos (participantes = UUID lógicos de identity_db, sin FK).
CREATE TABLE conversations (
    id                UUID PRIMARY KEY,
    property_id       UUID NULL,
    participant_a_id  UUID NOT NULL,
    participant_b_id  UUID NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_conversations_distinct_participants CHECK (participant_a_id <> participant_b_id)
);
CREATE INDEX idx_conversations_participant_a ON conversations(participant_a_id);
CREATE INDEX idx_conversations_participant_b ON conversations(participant_b_id);

CREATE TABLE chat_messages (
    id               UUID PRIMARY KEY,
    conversation_id  UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id        UUID NOT NULL,
    recipient_id     UUID NOT NULL,
    sender_name      VARCHAR(200) NOT NULL,
    content          TEXT NOT NULL,
    sent_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_chat_messages_conversation_sent ON chat_messages(conversation_id, sent_at);
CREATE INDEX idx_chat_messages_parties ON chat_messages(sender_id, recipient_id);

CREATE TABLE user_blocks (
    id          UUID PRIMARY KEY,
    blocker_id  UUID NOT NULL,
    blocked_id  UUID NOT NULL,
    reason      VARCHAR(250),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_blocks_pair UNIQUE (blocker_id, blocked_id)
);
CREATE INDEX idx_user_blocks_blocker ON user_blocks(blocker_id);
CREATE INDEX idx_user_blocks_blocked ON user_blocks(blocked_id);
