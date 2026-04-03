-- V18: User notification subsystem for ticket awareness
-- Tracks per-user unread/read state for ticket events (created, assigned, reassigned).

CREATE TABLE user_notifications (
    id               BIGSERIAL   PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    type             VARCHAR(100) NOT NULL,
    title            VARCHAR(500) NOT NULL,
    message          TEXT,
    ticket_id        BIGINT,
    ticket_public_id UUID,
    ticket_no        VARCHAR(50),
    group_id         BIGINT,
    actor_user_id    BIGINT,
    is_read          BOOLEAN     NOT NULL DEFAULT FALSE,
    read_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Efficient per-user unread count query
CREATE INDEX idx_user_notifications_user_unread
    ON user_notifications (user_id, is_read);

-- Ticket-scoped mark-read query
CREATE INDEX idx_user_notifications_ticket_id
    ON user_notifications (ticket_id);
