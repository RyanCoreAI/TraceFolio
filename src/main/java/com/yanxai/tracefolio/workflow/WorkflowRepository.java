package com.yanxai.tracefolio.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowRepository {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public WorkflowRepository(TraceFolioProperties properties, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID createRun(String workflowType, Object input) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rag_workflow_run (
                    id, tenant_id, workflow_type, status, current_step, input_json
                )
                VALUES (?, ?, ?, 'RUNNING', 'created', ?::jsonb)
                """,
                id,
                properties.tenantId(),
                workflowType,
                toJson(input)
        );
        return id;
    }

    public void addStep(UUID runId, int index, String name, String status, Object input, Object output, String error) {
        jdbcTemplate.update("""
                INSERT INTO rag_workflow_step (
                    id, run_id, step_index, step_name, status, input_json, output_json, error_message
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                """,
                UUID.randomUUID(),
                runId,
                index,
                name,
                status,
                toJson(input),
                toJson(output),
                error
        );
        jdbcTemplate.update("""
                UPDATE rag_workflow_run
                SET current_step = ?,
                    updated_at = now()
                WHERE id = ?
                """,
                name,
                runId
        );
    }

    public void complete(UUID runId, Object output) {
        finish(runId, "COMPLETED", "completed", output, null);
    }

    public void completeWithStatus(UUID runId, String status, Object output) {
        finish(runId, status, "completed", output, null);
    }

    public void fail(UUID runId, String currentStep, Object output, String error) {
        finish(runId, "FAILED", currentStep, output, error);
    }

    public WorkflowDecisionView recordDecision(UUID runId, WorkflowDecisionRequest request) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rag_workflow_decision (
                    id, run_id, step_name, decision, reason, metadata
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                id,
                runId,
                request.stepName(),
                request.normalizedDecision(),
                request.reason(),
                toJson(request.metadataOrEmpty())
        );
        return decisions(runId).stream()
                .filter(decision -> decision.decisionId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    public WorkflowRunView resume(UUID runId, WorkflowResumeRequest request) {
        WorkflowRunView current = find(runId);
        if (!List.of("WAITING_REVIEW", "FAILED_VALIDATION").contains(current.status())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "只有 WAITING_REVIEW 或 FAILED_VALIDATION 的 workflow 可以 resume。"
            );
        }
        addStep(runId, nextStepIndex(runId), "human_resume", "RESUMED", request, Map.of(
                "previousStatus", current.status(),
                "reason", request == null ? "" : request.reason()
        ), null);
        jdbcTemplate.update("""
                UPDATE rag_workflow_run
                SET status = 'RESUMED',
                    current_step = 'human_resume',
                    updated_at = now()
                WHERE id = ?
                """,
                runId
        );
        return find(runId);
    }

    public WorkflowRunView retryStep(UUID runId, int stepIndex, WorkflowRetryRequest request) {
        WorkflowStepView step = steps(runId).stream()
                .filter(item -> item.stepIndex() == stepIndex)
                .findFirst()
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "workflow step 不存在。"
                ));
        addStep(runId, nextStepIndex(runId), "retry_" + step.stepName(), "RETRIED", Map.of(
                "originalStepIndex", stepIndex,
                "originalStepName", step.stepName(),
                "reason", request == null ? "" : request.reason(),
                "overrideInput", request == null ? Map.of() : request.overrideInputOrEmpty()
        ), Map.of(
                "message", "已记录 retry 意图。TraceFolio 不会自动越过人工确认；需要调用对应业务 workflow 重新执行。"
        ), null);
        jdbcTemplate.update("""
                UPDATE rag_workflow_run
                SET status = 'RESUMED',
                    current_step = ?,
                    updated_at = now()
                WHERE id = ?
                """,
                "retry_" + step.stepName(),
                runId
        );
        return find(runId);
    }

    public WorkflowRunView find(UUID runId) {
        WorkflowRunView run = jdbcTemplate.queryForObject("""
                SELECT id, workflow_type, status, current_step, input_json,
                       output_json, error_message, created_at, updated_at
                FROM rag_workflow_run
                WHERE tenant_id = ?
                  AND id = ?
                """,
                this::mapRun,
                properties.tenantId(),
                runId
        );
        List<WorkflowStepView> steps = steps(runId);
        return new WorkflowRunView(
                run.runId(),
                run.workflowType(),
                run.status(),
                run.currentStep(),
                run.input(),
                run.output(),
                run.errorMessage(),
                run.createdAt(),
                run.updatedAt(),
                steps,
                decisions(runId)
        );
    }

    private List<WorkflowStepView> steps(UUID runId) {
        return jdbcTemplate.query("""
                SELECT step_index, step_name, status, input_json, output_json,
                       error_message, created_at
                FROM rag_workflow_step
                WHERE run_id = ?
                ORDER BY step_index ASC
                """,
                this::mapStep,
                runId
        );
    }

    private int nextStepIndex(UUID runId) {
        Integer value = jdbcTemplate.queryForObject(
                "SELECT coalesce(max(step_index), 0) + 1 FROM rag_workflow_step WHERE run_id = ?",
                Integer.class,
                runId
        );
        return value == null ? 1 : value;
    }

    private List<WorkflowDecisionView> decisions(UUID runId) {
        return jdbcTemplate.query("""
                SELECT id, step_name, decision, reason, metadata, created_at
                FROM rag_workflow_decision
                WHERE run_id = ?
                ORDER BY created_at DESC
                """,
                this::mapDecision,
                runId
        );
    }

    private void finish(UUID runId, String status, String currentStep, Object output, String error) {
        jdbcTemplate.update("""
                UPDATE rag_workflow_run
                SET status = ?,
                    current_step = ?,
                    output_json = ?::jsonb,
                    error_message = ?,
                    updated_at = now()
                WHERE id = ?
                """,
                status,
                currentStep,
                toJson(output),
                error,
                runId
        );
    }

    private WorkflowRunView mapRun(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowRunView(
                rs.getObject("id", UUID.class),
                rs.getString("workflow_type"),
                rs.getString("status"),
                rs.getString("current_step"),
                readObject(rs.getString("input_json")),
                readObject(rs.getString("output_json")),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                List.of(),
                List.of()
        );
    }

    private WorkflowStepView mapStep(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowStepView(
                rs.getInt("step_index"),
                rs.getString("step_name"),
                rs.getString("status"),
                readObject(rs.getString("input_json")),
                readObject(rs.getString("output_json")),
                rs.getString("error_message"),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private WorkflowDecisionView mapDecision(ResultSet rs, int rowNum) throws SQLException {
        return new WorkflowDecisionView(
                rs.getObject("id", UUID.class),
                rs.getString("step_name"),
                rs.getString("decision"),
                rs.getString("reason"),
                readMap(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private Object readObject(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize workflow json", e);
        }
    }
}
