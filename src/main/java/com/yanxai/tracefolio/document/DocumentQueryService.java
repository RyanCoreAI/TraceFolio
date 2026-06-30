package com.yanxai.tracefolio.document;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DocumentQueryService {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public DocumentQueryService(TraceFolioProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentSummary> listDocuments() {
        return jdbcTemplate.query("""
                SELECT d.id, d.source_type, d.title, d.source_uri, d.content_hash,
                       d.captured_at, d.created_at, COUNT(c.id) AS chunk_count
                FROM rag_document d
                LEFT JOIN rag_chunk c ON c.document_id = d.id
                WHERE d.tenant_id = ?
                GROUP BY d.id, d.source_type, d.title, d.source_uri,
                         d.content_hash, d.captured_at, d.created_at
                ORDER BY d.created_at DESC
                """,
                (rs, rowNum) -> new DocumentSummary(
                        rs.getObject("id", UUID.class),
                        rs.getString("source_type"),
                        rs.getString("title"),
                        rs.getString("source_uri"),
                        rs.getString("content_hash"),
                        rs.getInt("chunk_count"),
                        rs.getObject("captured_at", OffsetDateTime.class),
                        rs.getObject("created_at", OffsetDateTime.class)
                ),
                properties.tenantId()
        );
    }

    public KnowledgeStats stats() {
        return new KnowledgeStats(
                count("SELECT COUNT(*) FROM rag_document WHERE tenant_id = ?", properties.tenantId()),
                count("SELECT COUNT(*) FROM rag_chunk WHERE tenant_id = ?", properties.tenantId()),
                count("SELECT COUNT(*) FROM rag_query_trace WHERE tenant_id = ?", properties.tenantId()),
                count("SELECT COUNT(*) FROM rag_eval_run WHERE tenant_id = ?", properties.tenantId())
        );
    }

    private int count(String sql, String tenantId) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, tenantId);
        return value == null ? 0 : value;
    }
}
