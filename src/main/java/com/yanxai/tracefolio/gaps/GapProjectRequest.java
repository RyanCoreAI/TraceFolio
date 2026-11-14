package com.yanxai.tracefolio.gaps;

import java.util.List;
import java.util.UUID;

public record GapProjectRequest(
        UUID runId,
        List<GapInput> gaps,
        Integer targetDays
) {
}
