package com.yanxai.tracefolio.claims;

import java.util.List;
import java.util.Map;

public record EvidenceClaimValidation(
        String status,
        String freshness,
        List<String> errors,
        List<String> warnings
) {
    public Map<String, Object> asMetadata() {
        return Map.of(
                "validationStatus", status,
                "freshness", freshness,
                "validationErrors", errors,
                "validationWarnings", warnings
        );
    }
}
