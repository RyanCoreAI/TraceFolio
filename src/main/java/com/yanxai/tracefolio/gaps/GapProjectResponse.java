package com.yanxai.tracefolio.gaps;

import java.util.List;
import java.util.UUID;

public record GapProjectResponse(
        UUID runId,
        List<GapProjectPlan> projects
) {
}
