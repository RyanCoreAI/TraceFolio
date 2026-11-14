package com.yanxai.tracefolio.workflow;

import java.util.Map;

public record WorkflowResumeRequest(
        String reason,
        Map<String, Object> metadata
) {
    public Map<String, Object> metadataOrEmpty() {
        return metadata == null ? Map.of() : metadata;
    }
}
