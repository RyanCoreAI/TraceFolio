package com.yanxai.tracefolio.workflow;

import java.util.Map;

public record WorkflowRetryRequest(
        String reason,
        Map<String, Object> overrideInput
) {
    public Map<String, Object> overrideInputOrEmpty() {
        return overrideInput == null ? Map.of() : overrideInput;
    }
}
