package com.yanxai.tracefolio.document;

public record KnowledgeStats(
        int documents,
        int chunks,
        int traces,
        int evalRuns,
        int claims,
        int repoScans,
        int workflows,
        int portfolioExports
) {
}
