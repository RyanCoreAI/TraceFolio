package com.yanxai.tracefolio.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceOsAuditServiceTest {

    @Test
    void computesOverallStatusFromChecks() {
        assertThat(EvidenceOsAuditService.overallStatus(List.of(
                new EvidenceOsAuditCheck("a", "A", "PASS", "", ""),
                new EvidenceOsAuditCheck("b", "B", "PASS", "", "")
        ))).isEqualTo("READY");

        assertThat(EvidenceOsAuditService.overallStatus(List.of(
                new EvidenceOsAuditCheck("a", "A", "PASS", "", ""),
                new EvidenceOsAuditCheck("b", "B", "WARN", "", "")
        ))).isEqualTo("PARTIAL");

        assertThat(EvidenceOsAuditService.overallStatus(List.of(
                new EvidenceOsAuditCheck("a", "A", "WARN", "", ""),
                new EvidenceOsAuditCheck("b", "B", "FAIL", "", "")
        ))).isEqualTo("BLOCKED");
    }
}
