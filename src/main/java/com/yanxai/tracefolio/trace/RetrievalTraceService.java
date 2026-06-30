package com.yanxai.tracefolio.trace;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.qa.GroundedAnswer;
import com.yanxai.tracefolio.retrieval.RetrievalSummary;
import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RetrievalTraceService {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public RetrievalTraceService(
            TraceFolioProperties properties,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public UUID saveAnswered(
            String question,
            List<RetrievedChunk> chunks,
            RetrievalSummary summary,
            GroundedAnswer answer
    ) {
        UUID traceId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rag_query_trace (
                    id, tenant_id, question, status, answer, citations,
                    unsupported_claims, retrieval_summary
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb)
                """,
                traceId,
                properties.tenantId(),
                question,
                "ANSWERED",
                answer.answer(),
                toJson(answer.citationsOrEmpty()),
                toJson(answer.unsupportedClaimsOrEmpty()),
                toJson(summary)
        );
        saveRetrievedChunks(traceId, chunks);
        return traceId;
    }

    public UUID saveAbstained(
            String question,
            List<RetrievedChunk> chunks,
            RetrievalSummary summary,
            String reason
    ) {
        UUID traceId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rag_query_trace (
                    id, tenant_id, question, status, abstention_reason, retrieval_summary
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                traceId,
                properties.tenantId(),
                question,
                "ABSTAINED",
                reason,
                toJson(summary)
        );
        saveRetrievedChunks(traceId, chunks);
        return traceId;
    }

    public UUID saveFailedValidation(
            String question,
            List<RetrievedChunk> chunks,
            RetrievalSummary summary,
            GroundedAnswer answer,
            List<String> validationErrors
    ) {
        UUID traceId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO rag_query_trace (
                    id, tenant_id, question, status, answer, citations,
                    unsupported_claims, abstention_reason, retrieval_summary, validation_errors
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?::jsonb, ?::jsonb)
                """,
                traceId,
                properties.tenantId(),
                question,
                "FAILED_VALIDATION",
                answer.answer(),
                toJson(answer.citationsOrEmpty()),
                toJson(answer.unsupportedClaimsOrEmpty()),
                "CITATION_VALIDATION_FAILED",
                toJson(summary),
                toJson(validationErrors)
        );
        saveRetrievedChunks(traceId, chunks);
        return traceId;
    }

    public QueryTraceView find(UUID traceId) {
        QueryTraceView trace = jdbcTemplate.queryForObject("""
                SELECT id, question, status, answer, abstention_reason,
                       unsupported_claims, validation_errors, retrieval_summary, created_at
                FROM rag_query_trace
                WHERE id = ?
                """,
                (rs, rowNum) -> mapTrace(rs),
                traceId
        );

        List<RetrievedTraceChunk> chunks = jdbcTemplate.query("""
                SELECT rt.chunk_id, rt.vector_rank, rt.fts_rank, rt.final_rank,
                       rt.vector_score, rt.fts_score, rt.rrf_score,
                       c.title, c.heading_path, c.source_uri, c.content
                FROM rag_retrieved_chunk_trace rt
                JOIN rag_chunk c ON c.id = rt.chunk_id
                WHERE rt.trace_id = ?
                ORDER BY rt.final_rank ASC
                """,
                (rs, rowNum) -> new RetrievedTraceChunk(
                        rs.getObject("chunk_id", UUID.class),
                        (Integer) rs.getObject("vector_rank"),
                        (Integer) rs.getObject("fts_rank"),
                        rs.getInt("final_rank"),
                        (Double) rs.getObject("vector_score"),
                        (Double) rs.getObject("fts_score"),
                        rs.getDouble("rrf_score"),
                        rs.getString("title"),
                        rs.getString("heading_path"),
                        rs.getString("source_uri"),
                        excerpt(rs.getString("content"))
                ),
                traceId
        );

        return new QueryTraceView(
                trace.traceId(),
                trace.question(),
                trace.status(),
                trace.answer(),
                trace.abstentionReason(),
                trace.unsupportedClaims(),
                trace.validationErrors(),
                trace.retrievalSummary(),
                trace.createdAt(),
                chunks
        );
    }

    private QueryTraceView mapTrace(ResultSet rs) throws SQLException {
        return new QueryTraceView(
                rs.getObject("id", UUID.class),
                rs.getString("question"),
                rs.getString("status"),
                rs.getString("answer"),
                rs.getString("abstention_reason"),
                readList(rs.getString("unsupported_claims")),
                readList(rs.getString("validation_errors")),
                readSummary(rs.getString("retrieval_summary")),
                rs.getObject("created_at", OffsetDateTime.class),
                List.of()
        );
    }

    private void saveRetrievedChunks(UUID traceId, List<RetrievedChunk> chunks) {
        for (RetrievedChunk chunk : chunks) {
            jdbcTemplate.update("""
                    INSERT INTO rag_retrieved_chunk_trace (
                        id, trace_id, chunk_id, vector_rank, fts_rank, final_rank,
                        vector_score, fts_score, rrf_score, selected_for_context
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    UUID.randomUUID(),
                    traceId,
                    chunk.id(),
                    chunk.vectorRank(),
                    chunk.ftsRank(),
                    chunk.finalRank(),
                    chunk.vectorScore(),
                    chunk.ftsScore(),
                    chunk.rrfScore(),
                    true
            );
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize trace json", e);
        }
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    private RetrievalSummary readSummary(String json) {
        try {
            return objectMapper.readValue(json, RetrievalSummary.class);
        } catch (Exception e) {
            return new RetrievalSummary(0, 0, 0, 0.0);
        }
    }

    private String excerpt(String content) {
        if (content == null || content.length() <= 800) {
            return content;
        }
        return content.substring(0, 800);
    }
}
