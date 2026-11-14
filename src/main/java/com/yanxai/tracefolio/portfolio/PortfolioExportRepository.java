package com.yanxai.tracefolio.portfolio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
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
public class PortfolioExportRepository {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PortfolioExportRepository(TraceFolioProperties properties, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(PortfolioExportResponse response) {
        jdbcTemplate.update("""
                INSERT INTO rag_portfolio_export (
                    id, tenant_id, export_format, title, content, metadata, created_at
                )
                VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                response.exportId(),
                properties.tenantId(),
                response.format(),
                response.title(),
                response.content(),
                toJson(response.metadata()),
                response.createdAt()
        );
    }

    public PortfolioExportResponse find(UUID exportId) {
        return jdbcTemplate.queryForObject("""
                SELECT id, export_format, title, content, metadata, created_at
                FROM rag_portfolio_export
                WHERE tenant_id = ?
                  AND id = ?
                """,
                this::mapExport,
                properties.tenantId(),
                exportId
        );
    }

    public List<PortfolioExportResponse> list(int limit) {
        return jdbcTemplate.query("""
                SELECT id, export_format, title, content, metadata, created_at
                FROM rag_portfolio_export
                WHERE tenant_id = ?
                ORDER BY created_at DESC
                LIMIT ?
                """,
                this::mapExport,
                properties.tenantId(),
                Math.max(1, Math.min(limit, 100))
        );
    }

    private PortfolioExportResponse mapExport(ResultSet rs, int rowNum) throws SQLException {
        return new PortfolioExportResponse(
                rs.getObject("id", UUID.class),
                rs.getString("export_format"),
                rs.getString("title"),
                rs.getString("content"),
                readMap(rs.getString("metadata")),
                rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize portfolio export metadata", e);
        }
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
}
