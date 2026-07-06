package com.yanxai.tracefolio.resume;

import com.yanxai.tracefolio.jd.MatchedEvidence;
import java.util.List;

public record ResumeClaim(
        String requirementId,
        String status,
        String wording,
        List<MatchedEvidence> evidence
) {
}
