package com.yanxai.tracefolio.jd;

import java.util.List;
import java.util.UUID;

public record JdMatchRequest(
        UUID runId,
        String jdText,
        List<JdRequirement> requirements
) {
}
