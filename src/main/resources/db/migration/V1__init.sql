CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS rag_document (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    source_type VARCHAR(64) NOT NULL,
    title TEXT NOT NULL,
    source_uri TEXT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, source_uri, content_hash)
);

CREATE TABLE IF NOT EXISTS rag_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES rag_document(id) ON DELETE CASCADE,
    tenant_id VARCHAR(64) NOT NULL,
    chunk_index INT NOT NULL,
    content TEXT NOT NULL,
    title TEXT,
    heading_path TEXT,
    source_type VARCHAR(64) NOT NULL,
    source_uri TEXT NOT NULL,
    line_anchor TEXT,
    content_hash VARCHAR(128) NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1024) NOT NULL,
    fts tsvector GENERATED ALWAYS AS (
        to_tsvector(
            'simple',
            coalesce(title, '') || ' ' ||
            coalesce(heading_path, '') || ' ' ||
            coalesce(content, '')
        )
    ) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_rag_document_tenant ON rag_document (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_document_source_type ON rag_document (source_type);
CREATE INDEX IF NOT EXISTS idx_rag_document_metadata ON rag_document USING gin (metadata);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_tenant ON rag_chunk (tenant_id);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_source_type ON rag_chunk (source_type);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_metadata ON rag_chunk USING gin (metadata);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_fts ON rag_chunk USING gin (fts);
CREATE INDEX IF NOT EXISTS idx_rag_chunk_embedding_hnsw ON rag_chunk USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS rag_query_trace (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    answer TEXT,
    citations JSONB NOT NULL DEFAULT '[]'::jsonb,
    unsupported_claims JSONB NOT NULL DEFAULT '[]'::jsonb,
    abstention_reason TEXT,
    retrieval_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    validation_errors JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_retrieved_chunk_trace (
    id UUID PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES rag_query_trace(id) ON DELETE CASCADE,
    chunk_id UUID NOT NULL REFERENCES rag_chunk(id) ON DELETE CASCADE,
    vector_rank INT,
    fts_rank INT,
    final_rank INT NOT NULL,
    vector_score DOUBLE PRECISION,
    fts_score DOUBLE PRECISION,
    rrf_score DOUBLE PRECISION NOT NULL,
    selected_for_context BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS rag_eval_case (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    category VARCHAR(64) NOT NULL,
    question TEXT NOT NULL,
    expected_status VARCHAR(32) NOT NULL,
    required_terms JSONB NOT NULL DEFAULT '[]'::jsonb,
    forbidden_terms JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_eval_run (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    total_cases INT NOT NULL,
    passed_cases INT NOT NULL,
    failed_cases INT NOT NULL,
    summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS rag_eval_case_result (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES rag_eval_run(id) ON DELETE CASCADE,
    case_id UUID NOT NULL,
    trace_id UUID REFERENCES rag_query_trace(id) ON DELETE SET NULL,
    passed BOOLEAN NOT NULL,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
