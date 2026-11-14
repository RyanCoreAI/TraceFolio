package com.yanxai.tracefolio.jd;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.gaps.GapProjectPlan;
import com.yanxai.tracefolio.resume.ResumeDraft;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdRunRepository {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdRunRepository(TraceFolioProperties properties, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID createRun(String jdText, List<JdRequirement> requirements) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rag_jd_run (id, tenant_id, jd_text, requirements)
                VALUES (?, ?, ?, ?::jsonb)
                """,
                id,
                properties.tenantId(),
                jdText,
                toJson(requirements)
        );
        return id;
    }

    public void updateRequirements(UUID runId, List<JdRequirement> requirements) {
        updateJson(runId, "requirements", requirements);
    }

    public void updateEvidenceMatrix(UUID runId, List<EvidenceMatrixRow> matrix) {
        updateJson(runId, "evidence_matrix", matrix);
    }

    public void updateResumeDraft(UUID runId, ResumeDraft draft) {
        updateJson(runId, "resume_draft", draft == null ? Map.of() : draft);
    }

    public void updateGapProjects(UUID runId, List<GapProjectPlan> projects) {
        updateJson(runId, "gap_projects", projects);
    }

    public JdRunView find(UUID runId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, jd_text, requirements, evidence_matrix, resume_draft,
                       gap_projects, created_at, updated_at
                FROM rag_jd_run
                WHERE tenant_id = ?
                  AND id = ?
                """,
                this::mapRun,
                properties.tenantId(),
                runId
        );
    }

    private void updateJson(UUID runId, String column, Object value) {
        jdbcTemplate.update("""
                UPDATE rag_jd_run
                SET %s = ?::jsonb,
                    updated_at = now()
                WHERE tenant_id = ?
                  AND id = ?
                """.formatted(column),
                toJson(value),
                properties.tenantId(),
                runId
        );
    }

    private JdRunView mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new JdRunView(
                rs.getObject("id", UUID.class),
                rs.getString("jd_text"),
                readList(rs.getString("requirements"), new TypeReference<>() {}),
                readList(rs.getString("evidence_matrix"), new TypeReference<>() {}),
                readObject(rs.getString("resume_draft"), ResumeDraft.class),
                readList(rs.getString("gap_projects"), new TypeReference<>() {}),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JD run json", e);
        }
    }

    private <T> T readObject(String json, Class<T> type) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read JD run json", e);
        }
    }

    private <T> List<T> readList(String json, TypeReference<List<T>> typeReference) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read JD run json", e);
        }
    }
}
