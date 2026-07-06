CREATE TABLE IF NOT EXISTS rag_jd_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    jd_text TEXT NOT NULL,
    requirements JSONB NOT NULL DEFAULT '[]'::jsonb,
    evidence_matrix JSONB NOT NULL DEFAULT '[]'::jsonb,
    resume_draft JSONB NOT NULL DEFAULT '{}'::jsonb,
    gap_projects JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_jd_run_tenant ON rag_jd_run (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_jd_run_created_at ON rag_jd_run (created_at DESC);
