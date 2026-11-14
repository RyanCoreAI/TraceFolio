package com.yanxai.tracefolio.audit;

public record EvidenceOsAuditCheck(
        String id,
        String title,
        String status,
        String detail,
        String action
) {
}
