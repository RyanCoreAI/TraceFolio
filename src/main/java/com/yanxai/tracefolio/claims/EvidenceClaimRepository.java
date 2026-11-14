package com.yanxai.tracefolio.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EvidenceClaimRepository {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final EvidenceClaimValidator validator;

    public EvidenceClaimRepository(
            TraceFolioProperties properties,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            EvidenceClaimValidator validator
    ) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public List<SourceChunk> chunksForExtraction(int limit) {
        return jdbcTemplate.query("""
                SELECT id, document_id, source_type, source_uri, title,
                       heading_path, content, metadata, created_at
                FROM rag_chunk
                WHERE tenant_id = ?
                ORDER BY created_at DESC, chunk_index ASC
                LIMIT ?
                """,
                this::mapSourceChunk,
                properties.tenantId(),
                limit
        );
    }

    public void deleteTenantClaims() {
        jdbcTemplate.update("DELETE FROM rag_evidence_claim WHERE tenant_id = ?", properties.tenantId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public boolean insert(EvidenceClaimDraft claim) {
        EvidenceClaimValidation validation = validator.validate(claim);
        Map<String, Object> metadata = mergedMetadata(claim.metadata(), validation);
        String sourceRefType = safeSourceRefType(claim);
        String quoteHash = sha256(claim.quote());
        // The legacy unique constraint treats nullable source IDs/technology as distinct.
        // Serialize writes for the actual source, then compare NULLs as equal. This also
        // recognizes existing duplicates without deleting historical evidence.
        if ("CHUNK".equals(sourceRefType) && claim.sourceChunkId() != null) {
            jdbcTemplate.queryForObject("SELECT id FROM rag_chunk WHERE id = ? AND tenant_id = ? FOR UPDATE",
                    UUID.class, claim.sourceChunkId(), properties.tenantId());
        } else if ("REPO_SCAN".equals(sourceRefType) && claim.repoScanId() != null) {
            jdbcTemplate.queryForObject("SELECT id FROM rag_repo_scan WHERE id = ? AND tenant_id = ? FOR UPDATE",
                    UUID.class, claim.repoScanId(), properties.tenantId());
        } else {
            throw new IllegalArgumentException("Evidence claim requires a valid source reference");
        }
        Boolean exists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM rag_evidence_claim
                    WHERE tenant_id = ? AND source_ref_type = ?
                      AND source_chunk_id IS NOT DISTINCT FROM ?::uuid
                      AND repo_scan_id IS NOT DISTINCT FROM ?::uuid
                      AND capability = ? AND technology IS NOT DISTINCT FROM ?::text
                      AND quote_hash = ?
                )
                """, Boolean.class, properties.tenantId(), sourceRefType, claim.sourceChunkId(),
                claim.repoScanId(), claim.capability(), claim.technology(), quoteHash);
        if (Boolean.TRUE.equals(exists)) {
            return false;
        }
        int updated = jdbcTemplate.update("""
                INSERT INTO rag_evidence_claim (
                    id, tenant_id, source_ref_type, source_chunk_id, repo_scan_id, file_path,
                    claim_type, subject,
                    capability, action, technology, project_name, result_text,
                    source_type, title, source_uri, heading_path, quote, quote_hash,
                    strength, risk_level, confidence, validation_status, freshness, validated_at,
                    extraction_method, metadata
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), ?, ?::jsonb)
                ON CONFLICT (tenant_id, source_ref_type, source_chunk_id, repo_scan_id, capability, technology, quote_hash)
                DO NOTHING
                """,
                UUID.randomUUID(),
                properties.tenantId(),
                sourceRefType,
                claim.sourceChunkId(),
                claim.repoScanId(),
                claim.filePath(),
                claim.claimType(),
                claim.subject(),
                claim.capability(),
                claim.action(),
                claim.technology(),
                claim.projectName(),
                claim.resultText(),
                claim.sourceType(),
                claim.title(),
                claim.sourceUri(),
                claim.headingPath(),
                claim.quote(),
                quoteHash,
                claim.strength(),
                claim.riskLevel(),
                claim.confidence(),
                validation.status(),
                validation.freshness(),
                claim.extractionMethod(),
                toJson(metadata)
        );
        return updated > 0;
    }

    public int count() {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rag_evidence_claim WHERE tenant_id = ?",
                Integer.class,
                properties.tenantId()
        );
        return value == null ? 0 : value;
    }

    /** Review state for retrieved chunks must not depend on the top-200 claim list. */
    public Set<UUID> blockedSourceChunks(List<UUID> chunkIds) {
        if (chunkIds.isEmpty()) return Set.of();
        List<Object> args = new ArrayList<>();
        args.add(properties.tenantId());
        args.addAll(chunkIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(chunkIds.size(), "?"));
        return new HashSet<>(jdbcTemplate.query("""
                SELECT DISTINCT source_chunk_id FROM rag_evidence_claim
                WHERE tenant_id = ? AND source_chunk_id IN (%s)
                  AND (validation_status IS NULL OR validation_status <> 'VALID')
                """.formatted(placeholders), (rs, rowNum) -> rs.getObject("source_chunk_id", UUID.class), args.toArray()));
    }

    /** Saved JD evidence uses a chunk ID or, for a repository scan, a claim ID. */
    public boolean hasBlockedEvidenceReferences(List<UUID> evidenceIds) {
        if (evidenceIds.isEmpty()) return false;
        List<Object> args = new ArrayList<>();
        args.add(properties.tenantId());
        args.addAll(evidenceIds);
        args.addAll(evidenceIds);
        String placeholders = String.join(",", java.util.Collections.nCopies(evidenceIds.size(), "?"));
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1 FROM rag_evidence_claim WHERE tenant_id = ?
                      AND (id IN (%s) OR source_chunk_id IN (%s))
                      AND (validation_status IS NULL OR validation_status <> 'VALID')
                )
                """.formatted(placeholders, placeholders), Boolean.class, args.toArray()));
    }

    public List<EvidenceClaim> list(
            String projectName,
            String capability,
            String technology,
            String strength,
            Integer limit
    ) {
        return list(projectName, capability, technology, strength, null, null, null, null, limit);
    }

    public List<EvidenceClaim> list(
            String projectName,
            String capability,
            String technology,
            String strength,
            String riskLevel,
            String validationStatus,
            String sourceRefType,
            String freshness,
            Integer limit
    ) {
        List<Object> args = new ArrayList<>();
        args.add(properties.tenantId());
        StringBuilder sql = new StringBuilder("""
                SELECT id, source_ref_type, source_chunk_id, repo_scan_id, file_path,
                       claim_type, subject, capability,
                       action, technology, project_name, result_text, source_type,
                       title, source_uri, heading_path, quote, strength, risk_level,
                       confidence, validation_status, freshness, extraction_method, metadata, created_at
                FROM rag_evidence_claim
                WHERE tenant_id = ?
                """);

        appendLike(sql, args, "project_name", projectName);
        appendLike(sql, args, "capability", capability);
        appendLike(sql, args, "technology", technology);
        if (strength != null && !strength.isBlank()) {
            sql.append(" AND strength = ?");
            args.add(strength);
        }
        if (riskLevel != null && !riskLevel.isBlank()) {
            sql.append(" AND risk_level = ?");
            args.add(riskLevel.strip().toUpperCase());
        }
        if (validationStatus != null && !validationStatus.isBlank()) {
            sql.append(" AND validation_status = ?");
            args.add(validationStatus.strip().toUpperCase());
        }
        if (sourceRefType != null && !sourceRefType.isBlank()) {
            sql.append(" AND source_ref_type = ?");
            args.add(sourceRefType.strip().toUpperCase());
        }
        if (freshness != null && !freshness.isBlank()) {
            sql.append(" AND freshness = ?");
            args.add(freshness.strip().toUpperCase());
        }
        sql.append(" ORDER BY confidence DESC, created_at DESC LIMIT ?");
        args.add(limit == null || limit <= 0 ? 100 : Math.min(limit, 500));

        return jdbcTemplate.query(sql.toString(), this::mapClaim, args.toArray());
    }

    private void appendLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        sql.append(" AND ").append(column).append(" ILIKE ?");
        args.add("%" + value.strip() + "%");
    }

    private SourceChunk mapSourceChunk(ResultSet rs, int rowNum) throws SQLException {
        return new SourceChunk(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("source_type"),
                rs.getString("source_uri"),
                rs.getString("title"),
                rs.getString("heading_path"),
                rs.getString("content"),
                readMap(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private EvidenceClaim mapClaim(ResultSet rs, int rowNum) throws SQLException {
        return new EvidenceClaim(
                rs.getObject("id", UUID.class),
                rs.getString("source_ref_type"),
                rs.getObject("source_chunk_id", UUID.class),
                rs.getObject("repo_scan_id", UUID.class),
                rs.getString("file_path"),
                rs.getString("claim_type"),
                rs.getString("subject"),
                rs.getString("capability"),
                rs.getString("action"),
                rs.getString("technology"),
                rs.getString("project_name"),
                rs.getString("result_text"),
                rs.getString("source_type"),
                rs.getString("title"),
                rs.getString("source_uri"),
                rs.getString("heading_path"),
                rs.getString("quote"),
                rs.getString("strength"),
                rs.getString("risk_level"),
                rs.getDouble("confidence"),
                rs.getString("validation_status"),
                rs.getString("freshness"),
                rs.getString("extraction_method"),
                readMap(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize evidence claim metadata", e);
        }
    }

    private Map<String, Object> mergedMetadata(Map<String, Object> metadata, EvidenceClaimValidation validation) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (metadata != null) {
            merged.putAll(metadata);
        }
        merged.putAll(validation.asMetadata());
        return merged;
    }

    private String safeSourceRefType(EvidenceClaimDraft claim) {
        if (claim.sourceRefType() != null && !claim.sourceRefType().isBlank()) {
            return claim.sourceRefType();
        }
        return claim.repoScanId() == null ? "CHUNK" : "REPO_SCAN";
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash quote", e);
        }
    }
}
