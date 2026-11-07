package com.yanxai.tracefolio.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yanxai.tracefolio.claims.EvidenceClaimRepository;
import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.JdRunRepository;
import com.yanxai.tracefolio.jd.JdRunView;
import com.yanxai.tracefolio.jd.MatchedEvidence;
import com.yanxai.tracefolio.portfolio.PortfolioExportRepository;
import com.yanxai.tracefolio.portfolio.PortfolioExportRequest;
import com.yanxai.tracefolio.portfolio.PortfolioExportService;
import com.yanxai.tracefolio.resume.InterviewDefense;
import com.yanxai.tracefolio.resume.ResumeDraft;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class SavedEvidenceExportBoundaryTest {
    private final UUID runId = UUID.randomUUID();
    private final UUID evidenceId = UUID.randomUUID();
    private final EvidenceClaimRepository claims = mock(EvidenceClaimRepository.class);
    private final JdRunRepository jdRuns = mock(JdRunRepository.class);
    private final PortfolioExportRepository exports = mock(PortfolioExportRepository.class);
    private final PortfolioExportService service = new PortfolioExportService(claims, jdRuns, exports);
    private final PortfolioExportRequest request = new PortfolioExportRequest("Saved evidence", "MARKDOWN", runId, 30);

    @Test void savedMatrixCannotReExportAPendingReference() {
        stubSavedRun(false);
        when(claims.hasBlockedEvidenceReferences(List.of(evidenceId))).thenReturn(true);
        assertBlocked();
    }

    @Test void savedDefenseCardCannotBypassMatrixReviewCheck() {
        stubSavedRun(true);
        when(claims.hasBlockedEvidenceReferences(List.of(evidenceId))).thenReturn(true);
        assertBlocked();
    }

    @Test void aSavedRunWithoutBlockedReferencesStillExports() {
        stubSavedRun(false);
        var response = service.export(request);
        assertThat(response.content()).contains("RAG evidence quote");
        verify(exports).save(response);
    }

    private void assertBlocked() {
        assertThatThrownBy(() -> service.export(request)).isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(exports);
    }

    private void stubSavedRun(boolean defenseOnly) {
        var evidence = new MatchedEvidence(evidenceId, "GITHUB", "Evidence", "https://example.com/rag", "README",
                "RAG evidence quote", List.of("RAG"), 1, 1, null, 0.8);
        var row = new EvidenceMatrixRow("R1", "技术栈", "RAG", "MUST_HAVE", "SUPPORTED", 0.8,
                "RAG", "", List.of(evidence));
        var draft = defenseOnly ? new ResumeDraft(List.of(), List.of(), List.of(),
                List.of(new InterviewDefense("Question", "Answer", "Boundary", List.of(evidence))), "") : null;
        when(jdRuns.find(runId)).thenReturn(new JdRunView(runId, "RAG", List.of(),
                defenseOnly ? List.of() : List.of(row), draft, List.of(), OffsetDateTime.now(), OffsetDateTime.now()));
    }
}
