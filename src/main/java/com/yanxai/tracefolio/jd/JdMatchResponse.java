package com.yanxai.tracefolio.jd;

import java.util.List;
import java.util.UUID;

public record JdMatchResponse(
        UUID runId,
        JdMatchSummary summary,
        List<EvidenceMatrixRow> matrix
) {
}
