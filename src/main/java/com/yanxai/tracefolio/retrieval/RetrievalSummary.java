package com.yanxai.tracefolio.retrieval;

public record RetrievalSummary(
        int vectorHits,
        int ftsHits,
        int selectedChunks,
        double bestRrfScore
) {
}
