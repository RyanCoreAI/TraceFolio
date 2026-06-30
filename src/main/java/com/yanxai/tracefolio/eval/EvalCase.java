package com.yanxai.tracefolio.eval;

import java.util.List;
import java.util.UUID;

public record EvalCase(
        UUID id,
        String category,
        String question,
        String expectedStatus,
        List<String> requiredTerms,
        List<String> forbiddenTerms
) {
}
