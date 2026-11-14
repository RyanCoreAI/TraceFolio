package com.yanxai.tracefolio.workflow;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record WorkflowDecisionView(
        UUID decisionId,
        String stepName,
        String decision,
        String reason,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
