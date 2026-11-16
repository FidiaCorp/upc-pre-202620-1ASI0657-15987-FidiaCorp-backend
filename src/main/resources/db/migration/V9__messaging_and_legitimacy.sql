-- V9: User blocks and conversation messaging export (US17, US18)
CREATE TABLE IF NOT EXISTS user_blocks (
    id UUID PRIMARY KEY,
    blocker_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    blocked_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    reason VARCHAR(250),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_user_blocks_pair UNIQUE (blocker_id, blocked_id)
);

CREATE INDEX IF NOT EXISTS idx_user_blocks_blocker ON user_blocks(blocker_id);
CREATE INDEX IF NOT EXISTS idx_user_blocks_blocked ON user_blocks(blocked_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    sender_name VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    sent_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_conversation ON chat_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_parties ON chat_messages(sender_id, recipient_id);
