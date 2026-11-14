package com.yanxai.tracefolio.workflow;

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
import com.yanxai.tracefolio.resume.ResumeCompileResponse;
import com.yanxai.tracefolio.resume.ResumeCompiler;
import com.yanxai.tracefolio.resume.ResumeClaim;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class JdEvidenceWorkflowService {

    private final WorkflowRepository workflowRepository;
    private final EvidenceClaimExtractor claimExtractor;
    private final JdRequirementParser parser;
    private final JdEvidenceMatcher matcher;
    private final JdRunRepository jdRunRepository;
    private final ResumeCompiler resumeCompiler;
    private final GitHubProjectScout projectScout;

    public JdEvidenceWorkflowService(
            WorkflowRepository workflowRepository,
            EvidenceClaimExtractor claimExtractor,
            JdRequirementParser parser,
            JdEvidenceMatcher matcher,
            JdRunRepository jdRunRepository,
            ResumeCompiler resumeCompiler,
            GitHubProjectScout projectScout
    ) {
        this.workflowRepository = workflowRepository;
        this.claimExtractor = claimExtractor;
        this.parser = parser;
        this.matcher = matcher;
        this.jdRunRepository = jdRunRepository;
        this.resumeCompiler = resumeCompiler;
        this.projectScout = projectScout;
    }

    public WorkflowRunView run(WorkflowRunRequest request) {
        WorkflowRunRequest safeRequest = request == null ? new WorkflowRunRequest("", 5, true) : request;
        UUID runId = workflowRepository.createRun("JD_EVIDENCE", Map.of(
                "jdText", safeRequest.jdText(),
                "targetDays", safeRequest.safeTargetDays(),
                "extractClaims", safeRequest.shouldExtractClaims()
        ));

        UUID jdRunId = null;
        try {
            int step = 1;
            if (safeRequest.shouldExtractClaims()) {
                ClaimExtractResponse claims = claimExtractor.extract(new ClaimExtractRequest(false, 500));
                workflowRepository.addStep(runId, step++, "claim_extract", "COMPLETED", Map.of("limit", 500), claims, null);
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
            workflowRepository.addStep(runId, step, "gap_plan", "COMPLETED", Map.of(
                    "gapCount", gaps.size(),
                    "targetDays", safeRequest.safeTargetDays()
            ), gapProjects, null);

            Map<String, Object> output = output(jdRunId, matrix, resume, gapProjects, validation);
            if (validation.passed() && resume.refusedClaims().isEmpty()) {
                workflowRepository.complete(runId, output);
            } else {
                workflowRepository.completeWithStatus(runId, "WAITING_REVIEW", output);
            }
        } catch (Exception e) {
            workflowRepository.fail(runId, "failed", Map.of("jdRunId", jdRunId == null ? "" : jdRunId), e.getMessage());
        }
        return workflowRepository.find(runId);
    }

    public WorkflowRunView find(UUID runId) {
        return workflowRepository.find(runId);
    }

    public WorkflowDecisionView recordDecision(UUID runId, WorkflowDecisionRequest request) {
        return workflowRepository.recordDecision(runId, request);
    }

    public WorkflowRunView resume(UUID runId, WorkflowResumeRequest request) {
        return workflowRepository.resume(runId, request == null ? new WorkflowResumeRequest("", Map.of()) : request);
    }

    public WorkflowRunView retryStep(UUID runId, int stepIndex, WorkflowRetryRequest request) {
        return workflowRepository.retryStep(runId, stepIndex, request == null ? new WorkflowRetryRequest("", Map.of()) : request);
    }

    private Map<String, Object> output(
            UUID jdRunId,
            List<EvidenceMatrixRow> matrix,
            ResumeCompileResponse resume,
            List<GapProjectPlan> gapProjects,
            WorkflowValidationResult validation
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("jdRunId", jdRunId);
        output.put("matchSummary", matcher.summarize(matrix));
        output.put("resumeDraft", resume.draft());
        output.put("refusedClaims", resume.refusedClaims());
        output.put("gapProjects", gapProjects);
        output.put("validation", validation);
        output.put("humanReviewRequired", !resume.refusedClaims().isEmpty() || !validation.passed());
        output.put("portfolioExportRequest", Map.of(
                "title", "TraceFolio Portfolio Evidence Pack",
                "format", "MARKDOWN",
                "jdRunId", jdRunId,
                "maxClaims", 30
        ));
        return output;
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
