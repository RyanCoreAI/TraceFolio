package com.yanxai.tracefolio.jd;

import java.util.List;
import java.util.UUID;

public record JdCompileResponse(
        UUID runId,
        List<JdRequirement> requirements
) {
}
