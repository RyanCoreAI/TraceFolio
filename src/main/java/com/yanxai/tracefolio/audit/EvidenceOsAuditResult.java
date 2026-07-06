package com.yanxai.tracefolio.audit;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record EvidenceOsAuditResult(
        String status,
        int totalChecks,
        int passedChecks,
        Map<String, Object> metrics,
        List<EvidenceOsAuditCheck> checks,
        OffsetDateTime createdAt
) {
}
