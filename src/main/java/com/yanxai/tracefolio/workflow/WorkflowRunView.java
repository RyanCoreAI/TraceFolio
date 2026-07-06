package com.yanxai.tracefolio.workflow;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WorkflowRunView(
        UUID runId,
        String workflowType,
        String status,
        String currentStep,
        Object input,
        Object output,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<WorkflowStepView> steps,
        List<WorkflowDecisionView> decisions
) {
}
