package com.yanxai.tracefolio.retrieval;

import java.util.Map;
import java.util.UUID;

public record RetrievedChunk(
        UUID id,
        UUID documentId,
        String sourceType,
        String sourceUri,
        String title,
        String headingPath,
        String content,
        Map<String, Object> metadata,
        Integer vectorRank,
        Integer ftsRank,
        int finalRank,
        Double vectorScore,
        Double ftsScore,
        double rrfScore
) {
}
