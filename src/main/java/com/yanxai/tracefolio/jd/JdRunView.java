package com.yanxai.tracefolio.jd;

import com.yanxai.tracefolio.gaps.GapProjectPlan;
import com.yanxai.tracefolio.resume.ResumeDraft;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record JdRunView(
        UUID runId,
        String jdText,
        List<JdRequirement> requirements,
        List<EvidenceMatrixRow> evidenceMatrix,
        ResumeDraft resumeDraft,
        List<GapProjectPlan> gapProjects,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
