package com.yanxai.tracefolio.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.audit.EvidenceOsAuditCheck;
import com.yanxai.tracefolio.audit.EvidenceOsAuditResult;
import com.yanxai.tracefolio.audit.EvidenceOsAuditService;
import com.yanxai.tracefolio.claims.ClaimExtractResponse;
import com.yanxai.tracefolio.claims.EvidenceClaimExtractor;
import com.yanxai.tracefolio.gaps.GitHubProjectScout;
import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.JdEvidenceMatcher;
import com.yanxai.tracefolio.jd.JdMatchSummary;
import com.yanxai.tracefolio.jd.JdRequirement;
import com.yanxai.tracefolio.jd.JdRequirementParser;
import com.yanxai.tracefolio.jd.JdRunRepository;
import com.yanxai.tracefolio.jd.MatchedEvidence;
import com.yanxai.tracefolio.portfolio.PortfolioExportResponse;
import com.yanxai.tracefolio.portfolio.PortfolioExportService;
import com.yanxai.tracefolio.repo.GitHubRepoScanner;
import com.yanxai.tracefolio.repo.LocalRepoScanner;
import com.yanxai.tracefolio.resume.InterviewDefense;
import com.yanxai.tracefolio.resume.ProjectExperience;
import com.yanxai.tracefolio.resume.ResumeClaim;
import com.yanxai.tracefolio.resume.ResumeCompileResponse;
import com.yanxai.tracefolio.resume.ResumeCompiler;
import com.yanxai.tracefolio.resume.ResumeDraft;
import com.yanxai.tracefolio.roadmap.ProductRoadmapResponse;
import com.yanxai.tracefolio.roadmap.ProductRoadmapService;
import com.yanxai.tracefolio.roadmap.RoadmapPhase;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProductLoopWorkflowServiceTest {

    @Test
    void runsFullProductLoopAndPersistsPortfolioStep() {
        WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
        UUID workflowId = UUID.randomUUID();
        UUID jdRunId = UUID.randomUUID();
        when(workflowRepository.createRun(eq("PRODUCT_LOOP"), any())).thenReturn(workflowId);
        when(workflowRepository.find(workflowId)).thenReturn(new WorkflowRunView(
                workflowId,
                "PRODUCT_LOOP",
                "COMPLETED",
                "completed",
                new ObjectMapper().createObjectNode(),
                new ObjectMapper().createObjectNode(),
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                List.of(),
                List.of()
        ));

        EvidenceClaimExtractor claimExtractor = mock(EvidenceClaimExtractor.class);
        when(claimExtractor.extract(any())).thenReturn(new ClaimExtractResponse(1, 1, 1, 0, List.of()));

        JdRequirement requirement = new JdRequirement("REQ-001", "技术栈", "RAG", "MUST_HAVE", "README quote", "LOW", List.of("RAG"));
        JdRequirementParser parser = mock(JdRequirementParser.class);
        when(parser.parse("Java RAG JD")).thenReturn(List.of(requirement));

        JdRunRepository jdRunRepository = mock(JdRunRepository.class);
        when(jdRunRepository.createRun(eq("Java RAG JD"), anyList())).thenReturn(jdRunId);

        MatchedEvidence evidence = new MatchedEvidence(
                UUID.randomUUID(),
                "LOCAL_REPO_FILE",
                "TraceFolio",
                "local://RyanCoreAI/TraceFolio/README.md#L1",
                "README",
                "TraceFolio implements evidence-backed RAG.",
                List.of("RAG"),
                1,
                null,
                null,
                0.9
        );
        EvidenceMatrixRow row = new EvidenceMatrixRow("REQ-001", "技术栈", "RAG", "MUST_HAVE", "SUPPORTED", 0.9, "可写 RAG", "", List.of(evidence));
        JdEvidenceMatcher matcher = mock(JdEvidenceMatcher.class);
        when(matcher.match(List.of(requirement))).thenReturn(List.of(row));
        when(matcher.summarize(anyList())).thenReturn(new JdMatchSummary(1, 1, 0, 0, 0));

        ResumeDraft draft = new ResumeDraft(
                List.of("RAG"),
                List.of(new ProjectExperience("TraceFolio", evidence.sourceUri(), List.of("实现证据闭环。"))),
                List.of("证据驱动"),
                List.of(new InterviewDefense("如何证明？", "引用 README。", "不声称生产规模。", List.of(evidence))),
                "不生成伪造工作经历。"
        );
        ResumeCompileResponse resume = new ResumeCompileResponse(
                jdRunId,
                draft,
                List.of(new ResumeClaim("REQ-001", "SUPPORTED", "可写 RAG", List.of(evidence))),
                List.of()
        );
        ResumeCompiler resumeCompiler = mock(ResumeCompiler.class);
        when(resumeCompiler.compile(jdRunId, List.of(row))).thenReturn(resume);

        GitHubProjectScout projectScout = mock(GitHubProjectScout.class);
        when(projectScout.gapsFromMatrix(List.of(row))).thenReturn(List.of());

        PortfolioExportResponse export = new PortfolioExportResponse(
                UUID.randomUUID(),
                "MARKDOWN",
                "TraceFolio Product Evidence Pack",
                "# TraceFolio",
                Map.of("claimCount", 1),
                OffsetDateTime.now()
        );
        PortfolioExportService portfolioExportService = mock(PortfolioExportService.class);
        when(portfolioExportService.export(any())).thenReturn(export);

        EvidenceOsAuditService auditService = mock(EvidenceOsAuditService.class);
        when(auditService.audit()).thenReturn(new EvidenceOsAuditResult(
                "READY",
                1,
                1,
                Map.of(),
                List.of(new EvidenceOsAuditCheck("portfolio", "Portfolio", "PASS", "ok", "")),
                OffsetDateTime.now()
        ));

        ProductRoadmapService roadmapService = mock(ProductRoadmapService.class);
        when(roadmapService.roadmap()).thenReturn(new ProductRoadmapResponse(
                "TraceFolio",
                "Evidence OS",
                "PRODUCT_BASELINE_READY",
                90,
                Map.of(),
                List.of(),
                List.of(new RoadmapPhase("V6", "Portfolio", "RUNTIME_VERIFIED", 90, "Pack", List.of(), List.of(), List.of(), List.of())),
                List.of(),
                List.of(),
                OffsetDateTime.now()
        ));

        ProductLoopWorkflowService service = new ProductLoopWorkflowService(
                workflowRepository,
                mock(GitHubRepoScanner.class),
                mock(LocalRepoScanner.class),
                claimExtractor,
                parser,
                matcher,
                jdRunRepository,
                resumeCompiler,
                projectScout,
                portfolioExportService,
                auditService,
                roadmapService
        );

        WorkflowRunView result = service.run(new ProductLoopWorkflowRequest("Java RAG JD", null, false, true, 5, "MARKDOWN", 30));

        assertThat(result.runId()).isEqualTo(workflowId);
        ArgumentCaptor<String> stepCaptor = ArgumentCaptor.forClass(String.class);
        verify(workflowRepository, times(10)).addStep(eq(workflowId), anyInt(), stepCaptor.capture(), anyString(), any(), any(), any());
        assertThat(stepCaptor.getAllValues()).containsExactly(
                "repo_scan",
                "claim_extract",
                "jd_compile",
                "evidence_match",
                "resume_compile",
                "validate_resume_claims",
                "gap_plan",
                "portfolio_export",
                "evidence_os_audit",
                "product_readiness"
        );
        verify(portfolioExportService).export(any());
        verify(workflowRepository).complete(eq(workflowId), any());
    }
}
