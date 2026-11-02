package com.yanxai.tracefolio.document;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.yanxai.tracefolio.ai.OllamaEmbeddingClient;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.source.RawDocument;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class KnowledgeIngestServiceTest {
    private final KnowledgeRepository repository = mock(KnowledgeRepository.class);
    private final OllamaEmbeddingClient embeddings = mock(OllamaEmbeddingClient.class);
    private final HeadingAwareChunker chunker = mock(HeadingAwareChunker.class);
    private final TraceFolioProperties properties = new TraceFolioProperties(
            "tenant", new TraceFolioProperties.Embedding("model", 2, null), null, null, null, null);
    private final KnowledgeIngestService service = new KnowledgeIngestService(
            properties, new MarkdownCleaner(), chunker, repository, embeddings);
    private final RawDocument raw = new RawDocument(null, "MARKDOWN", "Doc", "source://doc", "body", Map.of());
    private final List<ChunkDraft> chunks = List.of(
            new ChunkDraft(0, "Doc", "A", "first", "L1", Map.of()),
            new ChunkDraft(1, "Doc", "B", "second", "L2", Map.of()));

    @Test
    void failureOnLaterEmbeddingNeverTouchesExistingDocumentOrEvidence() {
        when(chunker.chunk(anyString(), anyString())).thenReturn(chunks);
        when(embeddings.embed("first")).thenReturn(new float[]{1, 2});
        when(embeddings.embed("second")).thenThrow(new IllegalStateException("model unavailable"));
        assertThatThrownBy(() -> service.ingest(raw)).hasMessage("model unavailable");
        verifyNoInteractions(repository);
    }

    @Test
    void invalidVectorNeverReachesTheDatabase() {
        when(chunker.chunk(anyString(), anyString())).thenReturn(chunks);
        when(embeddings.embed("first")).thenReturn(new float[]{Float.NaN, 2});
        assertThatThrownBy(() -> service.ingest(raw)).hasMessageContaining("non-finite");
        verifyNoInteractions(repository);
    }

    @Test
    void completesAllEmbeddingCallsBeforeAtomicStore() {
        when(chunker.chunk(anyString(), anyString())).thenReturn(chunks);
        when(embeddings.embed(anyString())).thenReturn(new float[]{1, 2});
        when(repository.storeDocument(anyString(), anyString(), anyString(), anyString(), anyString(),
                anyMap(), anyList(), anyList(), anyList())).thenReturn(UUID.randomUUID());
        service.ingest(raw);
        InOrder order = inOrder(embeddings, repository);
        order.verify(embeddings).embed("first");
        order.verify(embeddings).embed("second");
        order.verify(repository).storeDocument(eq("tenant"), eq("MARKDOWN"), eq("Doc"), eq("source://doc"),
                anyString(), eq(Map.of()), eq(chunks), argThat(hashes -> hashes.size() == 2),
                argThat(vectors -> vectors.size() == 2));
    }
}
