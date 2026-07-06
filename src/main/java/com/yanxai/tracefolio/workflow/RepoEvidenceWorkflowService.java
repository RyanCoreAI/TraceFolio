package com.yanxai.tracefolio.workflow;

import com.yanxai.tracefolio.audit.EvidenceOsAuditResult;
import com.yanxai.tracefolio.audit.EvidenceOsAuditService;
import com.yanxai.tracefolio.repo.GitHubRepoScanner;
import com.yanxai.tracefolio.repo.LocalRepoScanner;
import com.yanxai.tracefolio.repo.RepoScanRequest;
import com.yanxai.tracefolio.repo.RepoScanResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RepoEvidenceWorkflowService {

    private final WorkflowRepository workflowRepository;
    private final GitHubRepoScanner githubScanner;
    private final LocalRepoScanner localScanner;
    private final EvidenceOsAuditService auditService;

    public RepoEvidenceWorkflowService(
            WorkflowRepository workflowRepository,
            GitHubRepoScanner githubScanner,
            LocalRepoScanner localScanner,
            EvidenceOsAuditService auditService
    ) {
        this.workflowRepository = workflowRepository;
        this.githubScanner = githubScanner;
        this.localScanner = localScanner;
        this.auditService = auditService;
    }

    public WorkflowRunView run(RepoEvidenceWorkflowRequest request) {
        RepoScanRequest scanRequest = request.repoScan();
        UUID runId = workflowRepository.createRun("REPO_EVIDENCE", Map.of("repoScan", scanRequest));
        try {
            workflowRepository.addStep(runId, 1, "validate_repo_scope", "COMPLETED", scanRequest, Map.of(
                    "source", scanRequest.safeSource(),
                    "confirmOwnership", scanRequest.ownershipConfirmed(),
                    "maxFiles", scanRequest.safeMaxFiles()
            ), null);

            RepoScanResponse scan = "LOCAL".equals(scanRequest.safeSource())
                    ? localScanner.scan(scanRequest)
                    : githubScanner.scan(scanRequest);
            workflowRepository.addStep(runId, 2, "repo_scan", "COMPLETED", scanRequest, scan, null);

            RepoEvidenceValidation validation = validate(scan);
            workflowRepository.addStep(runId, 3, "validate_repo_evidence", validation.passed() ? "COMPLETED" : "FAILED_VALIDATION", Map.of(
                    "scanId", scan.scanId()
            ), validation, validation.passed() ? null : validation.reason());

            EvidenceOsAuditResult audit = auditService.audit();
            workflowRepository.addStep(runId, 4, "evidence_os_audit", "COMPLETED", Map.of("scanId", scan.scanId()), audit, null);

            Map<String, Object> output = output(scan, validation, audit);
            if (validation.passed() && scan.warnings().stream().noneMatch(warning -> warning.contains("truncated"))) {
                workflowRepository.complete(runId, output);
            } else {
                workflowRepository.completeWithStatus(runId, "WAITING_REVIEW", output);
            }
        } catch (Exception e) {
            workflowRepository.fail(runId, "repo_evidence_failed", Map.of(), e.getMessage());
        }
        return workflowRepository.find(runId);
    }

    private RepoEvidenceValidation validate(RepoScanResponse scan) {
        if (scan.evidence().isEmpty()) {
            return new RepoEvidenceValidation(false, "仓库扫描没有发现可用工程证据。");
        }
        if (scan.insertedClaims() <= 0) {
            return new RepoEvidenceValidation(false, "仓库证据没有沉淀为 EvidenceClaim，可能全部重复或写入失败。");
        }
        return new RepoEvidenceValidation(true, "");
    }

    private Map<String, Object> output(
            RepoScanResponse scan,
            RepoEvidenceValidation validation,
            EvidenceOsAuditResult audit
    ) {
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("scanId", scan.scanId());
        output.put("sourceUri", scan.sourceUri());
        output.put("scannedFiles", scan.scannedFiles());
        output.put("evidenceItems", scan.evidence().size());
        output.put("insertedClaims", scan.insertedClaims());
        output.put("validation", validation);
        output.put("auditStatus", audit.status());
        output.put("humanReviewRequired", !validation.passed() || scan.warnings().stream().anyMatch(warning -> warning.contains("truncated")));
        return output;
    }

    private record RepoEvidenceValidation(boolean passed, String reason) {
    }
}
