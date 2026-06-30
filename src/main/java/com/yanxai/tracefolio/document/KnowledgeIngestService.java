package com.yanxai.tracefolio.document;

import com.yanxai.tracefolio.ai.OllamaEmbeddingClient;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.source.IngestResponse;
import com.yanxai.tracefolio.source.RawDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIngestService {

    private final TraceFolioProperties properties;
    private final MarkdownCleaner markdownCleaner;
    private final HeadingAwareChunker chunker;
    private final KnowledgeRepository repository;
    private final OllamaEmbeddingClient embeddingClient;

    public KnowledgeIngestService(
            TraceFolioProperties properties,
            MarkdownCleaner markdownCleaner,
            HeadingAwareChunker chunker,
            KnowledgeRepository repository,
            OllamaEmbeddingClient embeddingClient
    ) {
        this.properties = properties;
        this.markdownCleaner = markdownCleaner;
        this.chunker = chunker;
        this.repository = repository;
        this.embeddingClient = embeddingClient;
    }

    public IngestResponse ingest(RawDocument raw) {
        String cleaned = markdownCleaner.clean(raw.content());
        String documentHash = sha256(cleaned);

        UUID documentId = repository.upsertDocument(
                properties.tenantId(),
                raw.sourceType(),
                raw.title(),
                raw.sourceUri(),
                documentHash,
                raw.metadata()
        );

        List<ChunkDraft> chunks = chunker.chunk(raw.title(), cleaned);
        repository.deleteChunks(documentId);

        for (ChunkDraft chunk : chunks) {
            float[] embedding = embeddingClient.embed(chunk.content());
            validateDimensions(embedding);

            repository.insertChunk(
                    documentId,
                    properties.tenantId(),
                    raw.sourceType(),
                    raw.sourceUri(),
                    chunk,
                    sha256(chunk.content()),
                    embedding
            );
        }

        return new IngestResponse(documentId, chunks.size(), raw.sourceType(), raw.sourceUri());
    }

    private void validateDimensions(float[] embedding) {
        int expected = properties.embedding().dimensions();
        if (embedding.length != expected) {
            throw new IllegalStateException(
                    "Embedding dimensions mismatch. expected=" + expected + ", actual=" + embedding.length
            );
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
