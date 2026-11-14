package com.yanxai.tracefolio.repo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepoScanRepository {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RepoScanRepository(TraceFolioProperties properties, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID save(RepoScanResponse response) {
        UUID id = response.scanId() == null ? UUID.randomUUID() : response.scanId();
        jdbcTemplate.update("""
                INSERT INTO rag_repo_scan (
                    id, tenant_id, owner_name, repo_name, branch_name, source_uri,
                    scan_mode, commit_sha, scanned_files, evidence_summary,
                    evidence_items, warnings, selected_files, snapshot
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb, ?::jsonb)
                """,
                id,
                properties.tenantId(),
                response.owner(),
                response.repo(),
                response.branch(),
                response.sourceUri(),
                response.scanMode(),
                response.commitSha(),
                response.scannedFiles(),
                toJson(response.evidenceSummary()),
                toJson(response.evidence()),
                toJson(response.warnings()),
                toJson(response.selectedFiles()),
                toJson(response.snapshot())
        );
        return id;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize repo scan", e);
        }
    }
}
