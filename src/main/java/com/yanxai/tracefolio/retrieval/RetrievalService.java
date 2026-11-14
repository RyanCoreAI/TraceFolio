package com.yanxai.tracefolio.retrieval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.ai.OllamaEmbeddingClient;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RetrievalService {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final OllamaEmbeddingClient embeddingClient;
    private final RrfRanker rrfRanker;
    private final QueryNormalizer queryNormalizer;

    public RetrievalService(
            TraceFolioProperties properties,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            OllamaEmbeddingClient embeddingClient,
            RrfRanker rrfRanker,
            QueryNormalizer queryNormalizer
    ) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingClient = embeddingClient;
        this.rrfRanker = rrfRanker;
        this.queryNormalizer = queryNormalizer;
    }

    public RetrievalResult retrieve(String question) {
        QueryNormalizer.NormalizedQuery normalized = queryNormalizer.normalize(question);
        float[] queryEmbedding = embeddingClient.embed(normalized.expanded());
        String vector = toPgVectorLiteral(queryEmbedding);

        List<RetrievalCandidate> vectorHits = vectorSearch(vector);
        List<RetrievalCandidate> ftsHits = ftsSearch(normalized.expanded());
        List<RetrievedChunk> chunks = rrfRanker.rank(
                vectorHits,
                ftsHits,
                properties.retrieval().finalTopK()
        );

        RetrievalSummary summary = new RetrievalSummary(
                vectorHits.size(),
                ftsHits.size(),
                chunks.size(),
                chunks.stream().mapToDouble(RetrievedChunk::rrfScore).max().orElse(0.0),
                normalized.expanded(),
                normalized.expansions()
        );

        return new RetrievalResult(chunks, summary);
    }

    private List<RetrievalCandidate> vectorSearch(String vector) {
        return jdbcTemplate.query("""
                SELECT id, document_id, source_type, source_uri, title,
                       heading_path, content, metadata,
                       1 - (embedding <=> ?::vector) AS score
                FROM rag_chunk
                WHERE tenant_id = ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                (rs, rowNum) -> mapCandidate(rs, rowNum + 1, "VECTOR"),
                vector,
                properties.tenantId(),
                vector,
                properties.retrieval().vectorTopK()
        );
    }

    private List<RetrievalCandidate> ftsSearch(String question) {
        return jdbcTemplate.query("""
                SELECT id, document_id, source_type, source_uri, title,
                       heading_path, content, metadata,
                       ts_rank_cd(fts, websearch_to_tsquery('simple', ?))
                           + CASE WHEN lower(coalesce(title, '')) LIKE lower(?) THEN 0.2 ELSE 0 END
                           + CASE WHEN lower(coalesce(heading_path, '')) LIKE lower(?) THEN 0.1 ELSE 0 END AS score
                FROM rag_chunk
                WHERE tenant_id = ?
                  AND fts @@ websearch_to_tsquery('simple', ?)
                ORDER BY score DESC
                LIMIT ?
                """,
                (rs, rowNum) -> mapCandidate(rs, rowNum + 1, "FTS"),
                question,
                "%" + question + "%",
                "%" + question + "%",
                properties.tenantId(),
                question,
                properties.retrieval().ftsTopK()
        );
    }

    private RetrievalCandidate mapCandidate(ResultSet rs, int rank, String channel) throws SQLException {
        return new RetrievalCandidate(
                rs.getObject("id", UUID.class),
                rs.getObject("document_id", UUID.class),
                rs.getString("source_type"),
                rs.getString("source_uri"),
                rs.getString("title"),
                rs.getString("heading_path"),
                rs.getString("content"),
                readMetadata(rs.getString("metadata")),
                rank,
                channel,
                rs.getDouble("score")
        );
    }

    private Map<String, Object> readMetadata(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String toPgVectorLiteral(float[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
