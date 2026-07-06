ALTER TABLE rag_evidence_claim
    ADD COLUMN IF NOT EXISTS validation_status VARCHAR(32) NOT NULL DEFAULT 'VALID',
    ADD COLUMN IF NOT EXISTS freshness VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    ADD COLUMN IF NOT EXISTS validated_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_validation_status
    ON rag_evidence_claim (validation_status);

CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_freshness
    ON rag_evidence_claim (freshness);

ALTER TABLE rag_repo_scan
    ADD COLUMN IF NOT EXISTS scan_mode VARCHAR(32) NOT NULL DEFAULT 'FAST',
    ADD COLUMN IF NOT EXISTS commit_sha TEXT,
    ADD COLUMN IF NOT EXISTS selected_files JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS snapshot JSONB NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE rag_eval_run
    ADD COLUMN IF NOT EXISTS suite VARCHAR(64) NOT NULL DEFAULT 'core',
    ADD COLUMN IF NOT EXISTS tags JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_rag_eval_run_suite
    ON rag_eval_run (suite);
