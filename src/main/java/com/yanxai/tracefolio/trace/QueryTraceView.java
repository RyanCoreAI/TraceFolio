package com.yanxai.tracefolio.trace;

import com.yanxai.tracefolio.retrieval.RetrievalSummary;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record QueryTraceView(
        UUID traceId,
        String question,
        String status,
        String answer,
        String abstentionReason,
        List<String> unsupportedClaims,
        List<String> validationErrors,
        RetrievalSummary retrievalSummary,
        OffsetDateTime createdAt,
        List<RetrievedTraceChunk> retrievedChunks
) {
}
