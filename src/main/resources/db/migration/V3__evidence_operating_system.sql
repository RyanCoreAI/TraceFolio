CREATE TABLE IF NOT EXISTS rag_repo_scan (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    owner_name TEXT NOT NULL,
    repo_name TEXT NOT NULL,
    branch_name TEXT NOT NULL,
    source_uri TEXT NOT NULL,
    scanned_files INT NOT NULL,
    evidence_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    evidence_items JSONB NOT NULL DEFAULT '[]'::jsonb,
    warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_repo_scan_tenant ON rag_repo_scan (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_repo_scan_repo ON rag_repo_scan (owner_name, repo_name);
CREATE INDEX IF NOT EXISTS idx_rag_repo_scan_created_at ON rag_repo_scan (created_at DESC);

CREATE TABLE IF NOT EXISTS rag_evidence_claim (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    source_ref_type VARCHAR(32) NOT NULL,
    source_chunk_id UUID REFERENCES rag_chunk(id) ON DELETE CASCADE,
    repo_scan_id UUID REFERENCES rag_repo_scan(id) ON DELETE CASCADE,
    file_path TEXT,
    claim_type VARCHAR(64) NOT NULL,
    subject TEXT NOT NULL,
    capability TEXT NOT NULL,
    action TEXT NOT NULL,
    technology TEXT,
    project_name TEXT,
    result_text TEXT,
    source_type VARCHAR(64) NOT NULL,
    title TEXT,
    source_uri TEXT NOT NULL,
    heading_path TEXT,
    quote TEXT NOT NULL,
    quote_hash VARCHAR(64) NOT NULL,
    strength VARCHAR(32) NOT NULL,
    risk_level VARCHAR(32) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    extraction_method VARCHAR(64) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (
        (source_ref_type = 'CHUNK' AND source_chunk_id IS NOT NULL)
        OR
        (source_ref_type = 'REPO_SCAN' AND repo_scan_id IS NOT NULL)
    ),
    UNIQUE (tenant_id, source_ref_type, source_chunk_id, repo_scan_id, capability, technology, quote_hash)
);

CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_tenant ON rag_evidence_claim (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_source_chunk ON rag_evidence_claim (source_chunk_id);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_repo_scan ON rag_evidence_claim (repo_scan_id);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_source_ref_type ON rag_evidence_claim (source_ref_type);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_capability ON rag_evidence_claim (capability);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_technology ON rag_evidence_claim (technology);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_project ON rag_evidence_claim (project_name);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_strength ON rag_evidence_claim (strength);
CREATE INDEX IF NOT EXISTS idx_rag_evidence_claim_metadata ON rag_evidence_claim USING gin (metadata);

CREATE TABLE IF NOT EXISTS rag_workflow_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    workflow_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(128),
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_workflow_step (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES rag_workflow_run(id) ON DELETE CASCADE,
    step_index INT NOT NULL,
    step_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (run_id, step_index)
);

CREATE INDEX IF NOT EXISTS idx_rag_workflow_run_tenant ON rag_workflow_run (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_workflow_run_created_at ON rag_workflow_run (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_rag_workflow_step_run ON rag_workflow_step (run_id, step_index);

CREATE TABLE IF NOT EXISTS rag_workflow_decision (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES rag_workflow_run(id) ON DELETE CASCADE,
    step_name VARCHAR(128) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason TEXT,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_workflow_decision_run ON rag_workflow_decision (run_id, created_at DESC);

CREATE TABLE IF NOT EXISTS rag_portfolio_export (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    export_format VARCHAR(32) NOT NULL,
    title TEXT NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_rag_portfolio_export_tenant ON rag_portfolio_export (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_portfolio_export_created_at ON rag_portfolio_export (created_at DESC);
