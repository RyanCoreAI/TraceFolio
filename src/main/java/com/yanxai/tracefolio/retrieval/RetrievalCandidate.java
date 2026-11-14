package com.yanxai.tracefolio.retrieval;

import java.util.Map;
import java.util.UUID;

public record RetrievalCandidate(
        UUID id,
        UUID documentId,
        String sourceType,
        String sourceUri,
        String title,
        String headingPath,
        String content,
        Map<String, Object> metadata,
        int rank,
        String channel,
        double score
) {
}
