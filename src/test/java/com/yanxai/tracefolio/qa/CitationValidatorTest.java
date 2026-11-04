package com.yanxai.tracefolio.qa;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CitationValidatorTest {

    private final CitationValidator validator = new CitationValidator();

    @Test
    void passesWhenCitationChunkAndQuoteMatchEvidence() {
        UUID chunkId = UUID.randomUUID();
        RetrievedChunk chunk = chunk(chunkId, "RAG 不是外挂知识库，而是证据链系统。");
        GroundedAnswer answer = new GroundedAnswer(
                "ANSWERED",
                "RAG 是证据链系统。",
                List.of(new Citation(chunkId, "BLOG", "title", "https://example.com", "heading", "证据链系统")),
                List.of(),
                null
        );

        CitationValidationResult result = validator.validate(answer, List.of(chunk));

        assertThat(result.passed()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void failsWhenQuoteIsNotInChunk() {
        UUID chunkId = UUID.randomUUID();
        RetrievedChunk chunk = chunk(chunkId, "PatchBrake focuses on AI-generated diff safety.");
        GroundedAnswer answer = new GroundedAnswer(
                "ANSWERED",
                "PatchBrake 已经企业大规模采用。",
                List.of(new Citation(chunkId, "GITHUB_README", "title", "uri", "heading", "企业大规模采用")),
                List.of(),
                null
        );

        CitationValidationResult result = validator.validate(answer, List.of(chunk));

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).anyMatch(error -> error.contains("quote not found"));
    }

    private RetrievedChunk chunk(UUID id, String content) {
        return new RetrievedChunk(
                id,
                UUID.randomUUID(),
                "BLOG",
                "https://example.com",
                "title",
                "heading",
                content,
                Map.of(),
                1,
                null,
                1,
                0.9,
                null,
                0.1
        );
    }
}
