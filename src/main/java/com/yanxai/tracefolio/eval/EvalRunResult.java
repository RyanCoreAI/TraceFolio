package com.yanxai.tracefolio.eval;

import java.util.List;
import java.util.UUID;

public record EvalRunResult(
        UUID runId,
        int totalCases,
        int passedCases,
        int failedCases,
        List<String> failures
) {
}
