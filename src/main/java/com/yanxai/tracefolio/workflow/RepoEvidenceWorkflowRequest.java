package com.yanxai.tracefolio.workflow;

import com.yanxai.tracefolio.repo.RepoScanRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RepoEvidenceWorkflowRequest(
        @Valid @NotNull RepoScanRequest repoScan
) {
}
