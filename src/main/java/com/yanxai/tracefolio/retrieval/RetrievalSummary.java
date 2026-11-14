package com.yanxai.tracefolio.retrieval;

public record RetrievalSummary(
        int vectorHits,
        int ftsHits,
        int selectedChunks,
        double bestRrfScore,
        String normalizedQuestion,
        java.util.List<String> queryExpansions
) {
    public RetrievalSummary(
            int vectorHits,
            int ftsHits,
            int selectedChunks,
            double bestRrfScore
    ) {
        this(vectorHits, ftsHits, selectedChunks, bestRrfScore, "", java.util.List.of());
    }
}
