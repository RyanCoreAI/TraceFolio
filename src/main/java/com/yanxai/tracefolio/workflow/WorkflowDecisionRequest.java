package com.yanxai.tracefolio.workflow;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record WorkflowDecisionRequest(
        @NotBlank String stepName,
        @NotBlank String decision,
        String reason,
        Map<String, Object> metadata
) {
    public String normalizedDecision() {
        return decision == null ? "" : decision.strip().toUpperCase();
    }

    public Map<String, Object> metadataOrEmpty() {
        return metadata == null ? Map.of() : metadata;
    }
}
