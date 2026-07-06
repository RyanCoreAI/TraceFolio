package com.yanxai.tracefolio.workflow;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workflows")
public class WorkflowController {

    private final JdEvidenceWorkflowService workflowService;
    private final RepoEvidenceWorkflowService repoEvidenceWorkflowService;
    private final ProductLoopWorkflowService productLoopWorkflowService;

    public WorkflowController(
            JdEvidenceWorkflowService workflowService,
            RepoEvidenceWorkflowService repoEvidenceWorkflowService,
            ProductLoopWorkflowService productLoopWorkflowService
    ) {
        this.workflowService = workflowService;
        this.repoEvidenceWorkflowService = repoEvidenceWorkflowService;
        this.productLoopWorkflowService = productLoopWorkflowService;
    }

    @PostMapping("/jd-evidence/run")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowRunView runJdEvidence(@Valid @RequestBody WorkflowRunRequest request) {
        return workflowService.run(request);
    }

    @PostMapping("/repo-evidence/run")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowRunView runRepoEvidence(@Valid @RequestBody RepoEvidenceWorkflowRequest request) {
        return repoEvidenceWorkflowService.run(request);
    }

    @PostMapping("/product-loop/run")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowRunView runProductLoop(@Valid @RequestBody ProductLoopWorkflowRequest request) {
        return productLoopWorkflowService.run(request);
    }

    @GetMapping("/{runId}")
    public WorkflowRunView find(@PathVariable UUID runId) {
        return workflowService.find(runId);
    }

    @PostMapping("/{runId}/decisions")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowDecisionView decide(
            @PathVariable UUID runId,
            @Valid @RequestBody WorkflowDecisionRequest request
    ) {
        return workflowService.recordDecision(runId, request);
    }

    @PostMapping("/{runId}/resume")
    public WorkflowRunView resume(
            @PathVariable UUID runId,
            @RequestBody(required = false) WorkflowResumeRequest request
    ) {
        return workflowService.resume(runId, request);
    }

    @PostMapping("/{runId}/steps/{stepIndex}/retry")
    public WorkflowRunView retry(
            @PathVariable UUID runId,
            @PathVariable int stepIndex,
            @RequestBody(required = false) WorkflowRetryRequest request
    ) {
        return workflowService.retryStep(runId, stepIndex, request);
    }
}
