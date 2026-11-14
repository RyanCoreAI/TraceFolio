package com.yanxai.tracefolio.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

@Repository
public class KnowledgeRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public UUID storeDocument(
            String tenantId,
            String sourceType,
            String title,
            String sourceUri,
            String contentHash,
            Map<String, Object> metadata,
            List<ChunkDraft> chunks,
            List<String> chunkHashes,
            List<float[]> embeddings
    ) {
        if (chunks.size() != chunkHashes.size() || chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Every chunk requires a hash and embedding");
        }
        UUID documentId = upsertDocument(tenantId, sourceType, title, sourceUri, contentHash, metadata);
        jdbcTemplate.queryForObject("SELECT id FROM rag_document WHERE id = ? FOR UPDATE", UUID.class, documentId);
        List<StoredChunk> stored = jdbcTemplate.query(
                "SELECT chunk_index, content_hash FROM rag_chunk WHERE document_id = ? ORDER BY chunk_index",
                (rs, rowNum) -> new StoredChunk(rs.getInt("chunk_index"), rs.getString("content_hash")),
                documentId
        );
        boolean complete = stored.size() == chunks.size();
        for (int i = 0; complete && i < chunks.size(); i++) {
            complete = stored.get(i).index() == chunks.get(i).chunkIndex()
                    && stored.get(i).hash().equals(chunkHashes.get(i));
        }
        // Re-importing identical content must preserve chunk IDs and dependent evidence claims.
        if (!complete) {
            deleteChunks(documentId);
            for (int i = 0; i < chunks.size(); i++) {
                insertChunk(documentId, tenantId, sourceType, sourceUri,
                        chunks.get(i), chunkHashes.get(i), embeddings.get(i));
            }
        }
        return documentId;
    }

    private record StoredChunk(int index, String hash) {
    }

    public UUID upsertDocument(
            String tenantId,
            String sourceType,
            String title,
            String sourceUri,
            String contentHash,
            Map<String, Object> metadata
    ) {
        UUID id = UUID.randomUUID();

        jdbcTemplate.update("""
                INSERT INTO rag_document (
                    id, tenant_id, source_type, title, source_uri, content_hash, metadata
                )
                VALUES (?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (tenant_id, source_uri, content_hash)
                DO NOTHING
                """,
                id,
                tenantId,
                sourceType,
                title,
                sourceUri,
                contentHash,
                toJson(metadata)
        );

        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM rag_document
                WHERE tenant_id = ?
                  AND source_uri = ?
                  AND content_hash = ?
                """,
                UUID.class,
                tenantId,
                sourceUri,
                contentHash
        );
    }

    public void deleteChunks(UUID documentId) {
        jdbcTemplate.update("DELETE FROM rag_chunk WHERE document_id = ?", documentId);
    }

    public void insertChunk(
            UUID documentId,
            String tenantId,
            String sourceType,
            String sourceUri,
            ChunkDraft chunk,
            String contentHash,
            float[] embedding
    ) {
        jdbcTemplate.update("""
                INSERT INTO rag_chunk (
                    id, document_id, tenant_id, chunk_index, content,
                    title, heading_path, source_type, source_uri,
                    line_anchor, content_hash, metadata, embedding
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::vector)
                """,
                UUID.randomUUID(),
                documentId,
                tenantId,
                chunk.chunkIndex(),
                chunk.content(),
                chunk.title(),
                chunk.headingPath(),
                sourceType,
                sourceUri,
                chunk.lineAnchor(),
                contentHash,
                toJson(chunk.metadata()),
                toPgVectorLiteral(embedding)
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize json", e);
        }
    }

    private String toPgVectorLiteral(float[] vector) {
        return "[" + IntStream.range(0, vector.length)
                .mapToObj(i -> Float.toString(vector[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}
