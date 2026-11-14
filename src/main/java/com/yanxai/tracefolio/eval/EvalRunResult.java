package com.yanxai.tracefolio.eval;

import java.util.List;
import java.util.UUID;

public record EvalRunResult(
        UUID runId,
        String suite,
        int totalCases,
        int passedCases,
        int failedCases,
        double passRate,
        List<String> tags,
        List<String> failures
) {
    public EvalRunResult(
            UUID runId,
            int totalCases,
            int passedCases,
            int failedCases,
            List<String> failures
    ) {
        this(
                runId,
                "core",
                totalCases,
                passedCases,
                failedCases,
                totalCases == 0 ? 0.0 : Math.round((passedCases * 10000.0 / totalCases)) / 100.0,
                List.of(),
                failures
        );
    }
}
