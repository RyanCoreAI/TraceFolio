package com.yanxai.tracefolio.workflow;

import com.yanxai.tracefolio.audit.EvidenceOsAuditResult;
import com.yanxai.tracefolio.audit.EvidenceOsAuditService;
import com.yanxai.tracefolio.claims.ClaimExtractRequest;
import com.yanxai.tracefolio.claims.ClaimExtractResponse;
import com.yanxai.tracefolio.claims.EvidenceClaimExtractor;
import com.yanxai.tracefolio.gaps.GapInput;
import com.yanxai.tracefolio.gaps.GapProjectPlan;
import com.yanxai.tracefolio.gaps.GitHubProjectScout;
import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.JdEvidenceMatcher;
import com.yanxai.tracefolio.jd.JdRequirement;
import com.yanxai.tracefolio.jd.JdRequirementParser;
import com.yanxai.tracefolio.jd.JdRunRepository;
import com.yanxai.tracefolio.portfolio.PortfolioExportRequest;
import com.yanxai.tracefolio.portfolio.PortfolioExportResponse;
import com.yanxai.tracefolio.portfolio.PortfolioExportService;
import com.yanxai.tracefolio.repo.GitHubRepoScanner;
import com.yanxai.tracefolio.repo.LocalRepoScanner;
import com.yanxai.tracefolio.repo.RepoScanRequest;
import com.yanxai.tracefolio.repo.RepoScanResponse;
import com.yanxai.tracefolio.resume.ResumeClaim;
import com.yanxai.tracefolio.resume.ResumeCompileResponse;
import com.yanxai.tracefolio.resume.ResumeCompiler;
import com.yanxai.tracefolio.roadmap.ProductRoadmapResponse;
import com.yanxai.tracefolio.roadmap.ProductRoadmapService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ProductLoopWorkflowService {

    private final WorkflowRepository workflowRepository;
    private final GitHubRepoScanner gitHubRepoScanner;
    private final LocalRepoScanner localRepoScanner;
    private final EvidenceClaimExtractor claimExtractor;
    private final JdRequirementParser parser;
    private final JdEvidenceMatcher matcher;
    private final JdRunRepository jdRunRepository;
    private final ResumeCompiler resumeCompiler;
    private final GitHubProjectScout projectScout;
    private final PortfolioExportService portfolioExportService;
    private final EvidenceOsAuditService auditService;
    private final ProductRoadmapService roadmapService;

    public ProductLoopWorkflowService(
            WorkflowRepository workflowRepository,
            GitHubRepoScanner gitHubRepoScanner,
            LocalRepoScanner localRepoScanner,
            EvidenceClaimExtractor claimExtractor,
            JdRequirementParser parser,
            JdEvidenceMatcher matcher,
            JdRunRepository jdRunRepository,
            ResumeCompiler resumeCompiler,
            GitHubProjectScout projectScout,
            PortfolioExportService portfolioExportService,
            EvidenceOsAuditService auditService,
            ProductRoadmapService roadmapService
    ) {
        this.workflowRepository = workflowRepository;
        this.gitHubRepoScanner = gitHubRepoScanner;
        this.localRepoScanner = localRepoScanner;
        this.claimExtractor = claimExtractor;
        this.parser = parser;
        this.matcher = matcher;
        this.jdRunRepository = jdRunRepository;
        this.resumeCompiler = resumeCompiler;
        this.projectScout = projectScout;
        this.portfolioExportService = portfolioExportService;
        this.auditService = auditService;
        this.roadmapService = roadmapService;
    }

    public WorkflowRunView run(ProductLoopWorkflowRequest request) {
        ProductLoopWorkflowRequest safeRequest = request == null
                ? new ProductLoopWorkflowRequest("", null, false, true, 5, "MARKDOWN", 40)
                : request;
        UUID runId = workflowRepository.createRun("PRODUCT_LOOP", Map.of(
                "jdText", safeRequest.jdText(),
                "runRepoScan", safeRequest.shouldRunRepoScan(),
                "extractClaims", safeRequest.shouldExtractClaims(),
                "targetDays", safeRequest.safeTargetDays(),
                "portfolioFormat", safeRequest.safePortfolioFormat(),
                "maxClaims", safeRequest.safeMaxClaims()
        ));

        UUID jdRunId = null;
        try {
            int step = 1;
            RepoScanResponse repoScan = null;
            if (safeRequest.shouldRunRepoScan()) {
                repoScan = scanRepo(safeRequest.repoScan());
                workflowRepository.addStep(runId, step++, "repo_scan", "COMPLETED", safeRequest.repoScan(), repoScan, null);
            } else {
                workflowRepository.addStep(runId, step++, "repo_scan", "SKIPPED", Map.of("reason", "runRepoScan=false"), Map.of(), null);
            }

            ClaimExtractResponse claims = null;
            if (safeRequest.shouldExtractClaims()) {
                claims = claimExtractor.extract(new ClaimExtractRequest(false, 500));
                workflowRepository.addStep(runId, step++, "claim_extract", "COMPLETED", Map.of("limit", 500), claims, null);
            } else {
                workflowRepository.addStep(runId, step++, "claim_extract", "SKIPPED", Map.of("reason", "extractClaims=false"), Map.of(), null);
            }

            List<JdRequirement> requirements = parser.parse(safeRequest.jdText());
            jdRunId = jdRunRepository.createRun(safeRequest.jdText(), requirements);
            workflowRepository.addStep(runId, step++, "jd_compile", "COMPLETED", Map.of("jdRunId", jdRunId), Map.of(
                    "jdRunId", jdRunId,
                    "requirements", requirements
            ), null);

            List<EvidenceMatrixRow> matrix = matcher.match(requirements);
            jdRunRepository.updateEvidenceMatrix(jdRunId, matrix);
            workflowRepository.addStep(runId, step++, "evidence_match", "COMPLETED", Map.of("jdRunId", jdRunId), Map.of(
                    "summary", matcher.summarize(matrix),
                    "matrix", matrix
            ), null);

            ResumeCompileResponse resume = resumeCompiler.compile(jdRunId, matrix);
            jdRunRepository.updateResumeDraft(jdRunId, resume.draft());
            workflowRepository.addStep(runId, step++, "resume_compile", "COMPLETED", Map.of("jdRunId", jdRunId), resume, null);

            WorkflowValidationResult validation = validateResumeClaims(resume);
            workflowRepository.addStep(runId, step++, "validate_resume_claims", validation.passed() ? "COMPLETED" : "FAILED_VALIDATION", Map.of(
                    "jdRunId", jdRunId,
                    "claimCount", resume.claims().size()
            ), validation, validation.passed() ? null : String.join("; ", validation.errors()));

            List<GapInput> gaps = projectScout.gapsFromMatrix(matrix);
            List<GapProjectPlan> gapProjects = gaps.isEmpty()
                    ? List.of()
                    : projectScout.planProjects(gaps, safeRequest.safeTargetDays());
            jdRunRepository.updateGapProjects(jdRunId, gapProjects);
            workflowRepository.addStep(runId, step++, "gap_plan", "COMPLETED", Map.of(
                    "gapCount", gaps.size(),
                    "targetDays", safeRequest.safeTargetDays()
            ), gapProjects, null);

            PortfolioExportResponse export = portfolioExportService.export(new PortfolioExportRequest(
                    "TraceFolio Product Evidence Pack",
                    safeRequest.safePortfolioFormat(),
                    jdRunId,
                    safeRequest.safeMaxClaims()
            ));
            workflowRepository.addStep(runId, step++, "portfolio_export", "COMPLETED", Map.of(
                    "jdRunId", jdRunId,
                    "format", safeRequest.safePortfolioFormat(),
                    "maxClaims", safeRequest.safeMaxClaims()
            ), exportSummary(export), null);

            EvidenceOsAuditResult audit = auditService.audit();
            workflowRepository.addStep(runId, step++, "evidence_os_audit", "COMPLETED", Map.of("portfolioExportId", export.exportId()), audit, null);

            ProductRoadmapResponse roadmap = roadmapService.roadmap();
            workflowRepository.addStep(runId, step, "product_readiness", "COMPLETED", Map.of("auditStatus", audit.status()), Map.of(
                    "currentStage", roadmap.currentStage(),
                    "overallCompletion", roadmap.overallCompletion(),
                    "blockers", roadmap.currentBlockers()
            ), null);

            Map<String, Object> output = output(jdRunId, repoScan, claims, matrix, resume, gapProjects, validation, export, audit, roadmap);
            if (validation.passed() && resume.refusedClaims().isEmpty() && "READY".equals(audit.status())) {
                workflowRepository.complete(runId, output);
            } else {
                workflowRepository.completeWithStatus(runId, "WAITING_REVIEW", output);
            }
        } catch (Exception e) {
            workflowRepository.fail(runId, "failed", Map.of("jdRunId", jdRunId == null ? "" : jdRunId), e.getMessage());
        }
        return workflowRepository.find(runId);
    }

    private RepoScanResponse scanRepo(RepoScanRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("repoScan is required when runRepoScan=true");
        }
        return "LOCAL".equals(request.safeSource()) ? localRepoScanner.scan(request) : gitHubRepoScanner.scan(request);
    }

    private Map<String, Object> output(
            UUID jdRunId,
            RepoScanResponse repoScan,
            ClaimExtractResponse claims,
            List<EvidenceMatrixRow> matrix,
            ResumeCompileResponse resume,
            List<GapProjectPlan> gapProjects,
            WorkflowValidationResult validation,
            PortfolioExportResponse export,
            EvidenceOsAuditResult audit,
            ProductRoadmapResponse roadmap
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("jdRunId", jdRunId);
        output.put("repoScanId", repoScan == null ? null : repoScan.scanId());
        output.put("insertedRepoClaims", repoScan == null ? 0 : repoScan.insertedClaims());
        output.put("insertedChunkClaims", claims == null ? 0 : claims.insertedClaims());
        output.put("matchSummary", matcher.summarize(matrix));
        output.put("resumeDraft", resume.draft());
        output.put("refusedClaims", resume.refusedClaims());
        output.put("gapProjects", gapProjects);
        output.put("validation", validation);
        output.put("portfolioExport", exportSummary(export));
        output.put("auditStatus", audit.status());
        output.put("auditPassedChecks", audit.passedChecks());
        output.put("auditTotalChecks", audit.totalChecks());
        output.put("productStage", roadmap.currentStage());
        output.put("productCompletion", roadmap.overallCompletion());
        output.put("currentBlockers", roadmap.currentBlockers());
        output.put("humanReviewRequired", !resume.refusedClaims().isEmpty() || !validation.passed() || !"READY".equals(audit.status()));
        return output;
    }

    private Map<String, Object> exportSummary(PortfolioExportResponse export) {
        return Map.of(
                "exportId", export.exportId(),
                "format", export.format(),
                "title", export.title(),
                "metadata", export.metadata(),
                "contentUrl", "/api/portfolio/exports/%s/content".formatted(export.exportId()),
                "jsonUrl", "/api/portfolio/exports/%s".formatted(export.exportId())
        );
    }

    private WorkflowValidationResult validateResumeClaims(ResumeCompileResponse resume) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ResumeClaim claim : resume.claims()) {
            if (claim.evidence() == null || claim.evidence().isEmpty()) {
                errors.add(claim.requirementId() + " 缺少 evidence，不能进入简历。");
                continue;
            }
            boolean hasQuote = claim.evidence().stream()
                    .anyMatch(evidence -> evidence.quote() != null && !evidence.quote().isBlank());
            if (!hasQuote) {
                errors.add(claim.requirementId() + " 没有可追溯 quote，不能进入简历。");
            }
        }
        if (resume.claims().isEmpty()) {
            warnings.add("本次没有可写入简历的 claim，建议先补充 README、项目文档或运行证据。");
        }
        if (!resume.refusedClaims().isEmpty()) {
            warnings.add("存在拒写项，需要人工确认是否转为缺口项目。");
        }
        return new WorkflowValidationResult(errors.isEmpty(), errors, warnings);
    }

    private record WorkflowValidationResult(
            boolean passed,
            List<String> errors,
            List<String> warnings
    ) {
    }
}
