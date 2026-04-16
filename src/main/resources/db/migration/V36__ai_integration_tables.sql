-- ── V36: AI Integration Tables ──────────────────────────────────────────────
-- Introduces:
--   ticket_ai_index       — per-ticket AI indexing/sync state
--   ticket_ai_response_cache — cached AI responses by ticket + version + type
--   ai_ingestion_job      — async AI ingestion job history for observability

-- ── ticket_ai_index ──────────────────────────────────────────────────────────
CREATE TABLE ticket_ai_index (
    id                      BIGSERIAL PRIMARY KEY,
    ticket_id               BIGINT        NOT NULL UNIQUE REFERENCES tickets(id) ON DELETE CASCADE,
    source_version          BIGINT        NOT NULL DEFAULT 1,
    indexed_version         BIGINT        NOT NULL DEFAULT 0,
    sync_status             VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                            CHECK (sync_status IN ('PENDING','PROCESSING','SYNCED','FAILED','STALE','SKIPPED')),
    last_requested_at       TIMESTAMPTZ,
    processing_started_at   TIMESTAMPTZ,
    last_synced_at          TIMESTAMPTZ,
    consecutive_failure_count INT          NOT NULL DEFAULT 0,
    last_error_code         VARCHAR(50),
    last_error              TEXT,
    last_error_at           TIMESTAMPTZ,
    last_event_id           VARCHAR(100),
    last_correlation_id     VARCHAR(100),
    embedding_model         VARCHAR(100),
    index_schema_version    INT           NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ticket_ai_index_sync_status ON ticket_ai_index(sync_status);
CREATE INDEX idx_ticket_ai_index_stale ON ticket_ai_index(ticket_id)
    WHERE source_version > indexed_version;

-- ── ticket_ai_response_cache ─────────────────────────────────────────────────
CREATE TABLE ticket_ai_response_cache (
    id                  BIGSERIAL PRIMARY KEY,
    ticket_id           BIGINT        NOT NULL REFERENCES tickets(id) ON DELETE CASCADE,
    source_version      BIGINT        NOT NULL,
    response_type       VARCHAR(30)   NOT NULL
                        CHECK (response_type IN ('SUMMARY','REPLY_DRAFT','SIMILAR_CASES','POLICY_GUIDANCE')),
    response_payload    TEXT          NOT NULL,
    model_name          VARCHAR(100),
    prompt_version      VARCHAR(50),
    generated_at        TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    is_stale            BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

-- Unique per ticket + version + type (one cached response per combination)
CREATE UNIQUE INDEX idx_ai_cache_ticket_version_type
    ON ticket_ai_response_cache(ticket_id, source_version, response_type)
    WHERE is_stale = FALSE;

CREATE INDEX idx_ai_cache_ticket_stale ON ticket_ai_response_cache(ticket_id, is_stale);

-- ── ai_ingestion_job ─────────────────────────────────────────────────────────
CREATE TABLE ai_ingestion_job (
    id              BIGSERIAL PRIMARY KEY,
    job_id          VARCHAR(100)  NOT NULL UNIQUE,
    entity_type     VARCHAR(30)   NOT NULL,
    entity_id       BIGINT        NOT NULL,
    source_version  BIGINT        NOT NULL,
    topic           VARCHAR(100),
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','PROCESSING','SYNCED','FAILED','STALE','SKIPPED')),
    requested_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ,
    retry_count     INT           NOT NULL DEFAULT 0,
    error_message   TEXT,
    correlation_id  VARCHAR(100),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_ai_job_entity ON ai_ingestion_job(entity_type, entity_id);
CREATE INDEX idx_ai_job_status ON ai_ingestion_job(status);
CREATE INDEX idx_ai_job_correlation ON ai_ingestion_job(correlation_id);
