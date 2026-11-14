package com.yanxai.tracefolio.workflow;

import com.yanxai.tracefolio.repo.RepoScanRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

public record ProductLoopWorkflowRequest(
        @NotBlank(message = "jdText is required")
        String jdText,
        @Valid
        RepoScanRequest repoScan,
        Boolean runRepoScan,
        Boolean extractClaims,
        Integer targetDays,
        String portfolioFormat,
        Integer maxClaims
) {
    public boolean shouldRunRepoScan() {
        return Boolean.TRUE.equals(runRepoScan) || repoScan != null && runRepoScan == null;
    }

    public boolean shouldExtractClaims() {
        return extractClaims == null || extractClaims;
    }

    public int safeTargetDays() {
        if (targetDays == null) {
            return 5;
        }
        return Math.max(3, Math.min(targetDays, 7));
    }

    public String safePortfolioFormat() {
        return portfolioFormat == null || portfolioFormat.isBlank()
                ? "MARKDOWN"
                : portfolioFormat.strip().toUpperCase();
    }

    public int safeMaxClaims() {
        if (maxClaims == null || maxClaims <= 0) {
            return 40;
        }
        return Math.min(maxClaims, 120);
    }
}
