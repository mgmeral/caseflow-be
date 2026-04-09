-- V28: Structured note mentions, statusChangedAt on tickets, noteId on user_notifications

-- ── tickets: track when current workflow status began ─────────────────────────
-- Semantics: set whenever status changes; NULL for tickets pre-dating this column.
-- Frontend uses this for "waiting in current status" durations.
ALTER TABLE tickets ADD COLUMN status_changed_at TIMESTAMPTZ;

-- ── user_notifications: link mention notifications to their source note ────────
ALTER TABLE user_notifications ADD COLUMN note_id BIGINT;

-- ── note_mentions: structured relation — one row per (note, mentioned user) ───
-- Persisted at note-creation time from the mentionedUserIds input.
-- Backend never parses raw text to reconstruct mentions after the fact.
CREATE TABLE note_mentions (
    id         BIGSERIAL    PRIMARY KEY,
    note_id    BIGINT       NOT NULL,
    user_id    BIGINT       NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (note_id, user_id)
);

CREATE INDEX idx_note_mentions_note_id ON note_mentions (note_id);
CREATE INDEX idx_note_mentions_user_id ON note_mentions (user_id);
