package com.yanxai.tracefolio.repo;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record RepoScanResponse(
        UUID scanId,
        String owner,
        String repo,
        String branch,
        String sourceUri,
        String scanMode,
        String commitSha,
        int scannedFiles,
        int insertedClaims,
        Map<String, Integer> evidenceSummary,
        List<RepoEvidenceItem> evidence,
        List<String> warnings,
        List<String> selectedFiles,
        Map<String, Object> snapshot
) {
    public RepoScanResponse(
            UUID scanId,
            String owner,
            String repo,
            String branch,
            String sourceUri,
            int scannedFiles,
            int insertedClaims,
            Map<String, Integer> evidenceSummary,
            List<RepoEvidenceItem> evidence,
            List<String> warnings
    ) {
        this(
                scanId,
                owner,
                repo,
                branch,
                sourceUri,
                "FAST",
                null,
                scannedFiles,
                insertedClaims,
                evidenceSummary,
                evidence,
                warnings,
                List.of(),
                Map.of()
        );
    }
}
