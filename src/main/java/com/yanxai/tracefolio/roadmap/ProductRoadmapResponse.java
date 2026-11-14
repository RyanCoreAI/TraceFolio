package com.yanxai.tracefolio.roadmap;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ProductRoadmapResponse(
        String productName,
        String positioning,
        String currentStage,
        int overallCompletion,
        Map<String, Object> runtimeEvidence,
        List<String> differentiators,
        List<RoadmapPhase> phases,
        List<String> releaseGate,
        List<String> currentBlockers,
        OffsetDateTime generatedAt
) {
}
