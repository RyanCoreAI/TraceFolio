package com.yanxai.tracefolio.roadmap;

import java.util.List;

public record RoadmapPhase(
        String id,
        String title,
        String status,
        int completion,
        String productOutcome,
        List<String> delivered,
        List<String> acceptanceCriteria,
        List<String> openRisks,
        List<String> apiContracts
) {
}
