package com.yanxai.tracefolio.workflow;

import java.time.OffsetDateTime;

public record WorkflowStepView(
        int stepIndex,
        String stepName,
        String status,
        Object input,
        Object output,
        String errorMessage,
        OffsetDateTime createdAt
) {
}
