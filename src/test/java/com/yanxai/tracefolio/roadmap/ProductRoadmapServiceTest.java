package com.yanxai.tracefolio.roadmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yanxai.tracefolio.audit.EvidenceOsAuditCheck;
import com.yanxai.tracefolio.audit.EvidenceOsAuditResult;
import com.yanxai.tracefolio.audit.EvidenceOsAuditService;
import com.yanxai.tracefolio.document.DocumentQueryService;
import com.yanxai.tracefolio.document.KnowledgeStats;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProductRoadmapServiceTest {

    @Test
    void reportsFullRoadmapWithRuntimeEvidence() {
        DocumentQueryService documentQueryService = mock(DocumentQueryService.class);
        EvidenceOsAuditService auditService = mock(EvidenceOsAuditService.class);
        when(documentQueryService.stats()).thenReturn(new KnowledgeStats(2, 12, 3, 1, 9, 1, 2, 1));
        when(auditService.audit()).thenReturn(new EvidenceOsAuditResult(
                "READY",
                2,
                2,
                Map.of("claims", 9),
                List.of(new EvidenceOsAuditCheck("claims", "Claims", "PASS", "ok", "")),
                OffsetDateTime.now()
        ));

        ProductRoadmapResponse response = new ProductRoadmapService(documentQueryService, auditService).roadmap();

        assertThat(response.productName()).isEqualTo("TraceFolio");
        assertThat(response.currentStage()).isEqualTo("PRODUCT_BASELINE_READY");
        assertThat(response.phases()).hasSize(6);
        assertThat(response.phases())
                .extracting(RoadmapPhase::id)
                .containsExactly("V1", "V2", "V3", "V4", "V5", "V6");
        assertThat(response.currentBlockers()).isEmpty();
    }
}
