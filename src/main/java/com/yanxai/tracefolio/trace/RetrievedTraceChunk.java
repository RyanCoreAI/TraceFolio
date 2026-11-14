package com.yanxai.tracefolio.trace;

import java.util.UUID;

public record RetrievedTraceChunk(
        UUID chunkId,
        Integer vectorRank,
        Integer ftsRank,
        int finalRank,
        Double vectorScore,
        Double ftsScore,
        double rrfScore,
        String title,
        String headingPath,
        String sourceUri,
        String content
) {
}
