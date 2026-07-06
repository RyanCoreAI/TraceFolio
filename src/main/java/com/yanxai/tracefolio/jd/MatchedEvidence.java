package com.yanxai.tracefolio.jd;

import java.util.List;
import java.util.UUID;

public record MatchedEvidence(
        UUID chunkId,
        String sourceType,
        String title,
        String sourceUri,
        String headingPath,
        String quote,
        List<String> matchedKeywords,
        int finalRank,
        Integer vectorRank,
        Integer ftsRank,
        double rrfScore
) {
}
