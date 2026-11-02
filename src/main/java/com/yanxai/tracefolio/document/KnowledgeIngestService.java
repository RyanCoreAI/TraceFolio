package com.yanxai.tracefolio.document;

import com.yanxai.tracefolio.ai.OllamaEmbeddingClient;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.source.IngestResponse;
import com.yanxai.tracefolio.source.RawDocument;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
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

        List<ChunkDraft> chunks = chunker.chunk(raw.title(), cleaned);
        List<float[]> embeddings = new ArrayList<>(chunks.size());
        for (ChunkDraft chunk : chunks) {
            float[] embedding = embeddingClient.embed(chunk.content());
            validateDimensions(embedding);
            embeddings.add(embedding);
        }

        // Model/network calls must finish before the short database transaction starts.
        UUID documentId = repository.storeDocument(
                properties.tenantId(),
                raw.sourceType(),
                raw.title(),
                raw.sourceUri(),
                documentHash,
                raw.metadata(),
                chunks,
                chunks.stream().map(chunk -> sha256(chunk.content())).toList(),
                embeddings
        );

        return new IngestResponse(documentId, chunks.size(), raw.sourceType(), raw.sourceUri());
    }

    private void validateDimensions(float[] embedding) {
        int expected = properties.embedding().dimensions();
        if (embedding == null || embedding.length != expected) {
            throw new IllegalStateException(
                    "Embedding dimensions mismatch. expected=" + expected + ", actual="
                            + (embedding == null ? "null" : embedding.length)
            );
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Embedding contains non-finite values");
            }
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
