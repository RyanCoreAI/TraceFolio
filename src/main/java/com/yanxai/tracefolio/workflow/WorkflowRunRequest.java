package com.yanxai.tracefolio.workflow;

import jakarta.validation.constraints.NotBlank;

public record WorkflowRunRequest(
        @NotBlank String jdText,
        Integer targetDays,
        Boolean extractClaims
) {
    public int safeTargetDays() {
        if (targetDays == null || targetDays <= 0) {
            return 5;
        }
        return Math.max(3, Math.min(targetDays, 7));
    }

    public boolean shouldExtractClaims() {
        return extractClaims == null || extractClaims;
    }
}
