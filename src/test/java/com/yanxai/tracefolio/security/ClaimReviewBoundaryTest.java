package com.yanxai.tracefolio.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.yanxai.tracefolio.claims.*;
import com.yanxai.tracefolio.jd.*;
import com.yanxai.tracefolio.portfolio.*;
import com.yanxai.tracefolio.retrieval.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class ClaimReviewBoundaryTest {
    private final EvidenceClaimRepository claims = mock(EvidenceClaimRepository.class);
    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final UUID chunkId = UUID.randomUUID();
    private final JdRequirement requirement = new JdRequirement("R1", "技术栈", "RAG", "MUST_HAVE", "source", "LOW", List.of("RAG"));

    private EvidenceClaim claim(String status, UUID sourceId) {
        return new EvidenceClaim(UUID.randomUUID(), "CHUNK", sourceId, null, null, "CAPABILITY", "RAG",
                "RAG", "RAG implementation", "RAG", "Project", "Result", "BLOG", "RAG evidence",
                "https://example.com/evidence", "RAG", "RAG evidence awaiting independent review", "WEAK", "HIGH",
                0.9, status, "UNKNOWN", "RULE", Map.of(), OffsetDateTime.now());
    }

    @Test void pendingAndUnknownClaimsDoNotSupportRequirements() {
        for (String status : new String[]{"NEEDS_REVIEW", "UNRECOGNIZED", null}) {
            when(claims.list(null, null, null, null, 200)).thenReturn(List.of(claim(status, chunkId)));
            when(retrieval.retrieve(anyString())).thenReturn(new RetrievalResult(List.of(), null));
            var row = new JdEvidenceMatcher(retrieval, claims).match(List.of(requirement)).getFirst();
            assertThat(row.supportStatus()).isEqualTo("GAP_PROJECT_REQUIRED");
            assertThat(row.evidence()).isEmpty();
        }
    }

    @Test void pendingClaimsCannotReenterThroughRawChunkFallback() {
        when(claims.list(null, null, null, null, 200)).thenReturn(List.of(claim("NEEDS_REVIEW", chunkId)));
        var chunk = new RetrievedChunk(chunkId, UUID.randomUUID(), "BLOG", "https://example.com/evidence", "RAG",
                "RAG", "RAG evidence awaiting independent review", Map.of(), 1, 1, 1, 0.9, 0.9, 0.9);
        when(retrieval.retrieve(anyString())).thenReturn(new RetrievalResult(List.of(chunk), null));
        var row = new JdEvidenceMatcher(retrieval, claims).match(List.of(requirement)).getFirst();
        assertThat(row.evidence()).isEmpty();
    }

    @Test void onlyValidatedClaimsEnterPortfolioEvidencePages() {
        when(claims.list(null, null, null, null, 30)).thenReturn(Arrays.asList(
                claim("NEEDS_REVIEW", chunkId), claim(null, UUID.randomUUID()), claim("VALID", UUID.randomUUID())));
        var service = new PortfolioExportService(claims, mock(JdRunRepository.class), mock(PortfolioExportRepository.class));
        var export = service.export(new PortfolioExportRequest("Review boundary", "MARKDOWN", null, 30));
        assertThat(export.metadata()).containsEntry("claimCount", 1);
    }

    @Test void rejectedChunksOutsideTheTopClaimWindowStillCannotEnter() {
        when(claims.list(null, null, null, null, 200)).thenReturn(List.of());
        var chunk = new RetrievedChunk(chunkId, UUID.randomUUID(), "BLOG", "https://example.com/evidence", "RAG",
                "RAG", "RAG old rejected evidence", Map.of(), 1, 1, 1, 0.9, 0.9, 0.9);
        when(retrieval.retrieve(anyString())).thenReturn(new RetrievalResult(List.of(chunk), null));
        when(claims.blockedSourceChunks(List.of(chunkId))).thenReturn(Set.of(chunkId));
        assertThat(new JdEvidenceMatcher(retrieval, claims).match(List.of(requirement)).getFirst().evidence()).isEmpty();
    }

    @Test void failedReviewLookupCannotSilentlyFallBackToRawEvidence() {
        when(claims.list(null, null, null, null, 200)).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(new JdEvidenceMatcher(retrieval, claims).match(List.of(requirement)).getFirst().evidence()).isEmpty();
        verifyNoInteractions(retrieval);
    }

    @Test void aValidatedClaimStillSupportsMatching() {
        when(claims.list(null, null, null, null, 200)).thenReturn(List.of(claim("VALID", chunkId)));
        var row = new JdEvidenceMatcher(retrieval, claims).match(List.of(requirement)).getFirst();
        assertThat(row.supportStatus()).isEqualTo("WEAKLY_SUPPORTED");
        verifyNoInteractions(retrieval);
    }
}
