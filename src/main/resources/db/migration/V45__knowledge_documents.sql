-- V45: Knowledge base documents (AI-001-BE-KB)
--
-- Admin-managed policy / knowledge texts that caseflow-ai-service indexes for policy guidance
-- and reply-draft grounding. customer_id NULL means the document applies to every customer
-- (indexed as GLOBAL); otherwise only that customer's tickets retrieve it.
CREATE TABLE knowledge_documents (
    id           BIGSERIAL PRIMARY KEY,
    public_id    UUID         NOT NULL UNIQUE,
    title        VARCHAR(255) NOT NULL,
    body         TEXT         NOT NULL,
    category     VARCHAR(100),
    customer_id  BIGINT       REFERENCES customers (id),
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_by   BIGINT,
    updated_by   BIGINT,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_knowledge_documents_customer ON knowledge_documents (customer_id);
CREATE INDEX idx_knowledge_documents_active ON knowledge_documents (is_active);
