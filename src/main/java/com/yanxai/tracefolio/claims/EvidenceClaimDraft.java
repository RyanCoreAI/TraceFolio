package com.yanxai.tracefolio.claims;

import java.util.Map;
import java.util.UUID;

public record EvidenceClaimDraft(
        String sourceRefType,
        UUID sourceChunkId,
        UUID repoScanId,
        String filePath,
        String claimType,
        String subject,
        String capability,
        String action,
        String technology,
        String projectName,
        String resultText,
        String sourceType,
        String title,
        String sourceUri,
        String headingPath,
        String quote,
        String strength,
        String riskLevel,
        double confidence,
        String validationStatus,
        String freshness,
        String extractionMethod,
        Map<String, Object> metadata
) {
    public EvidenceClaimDraft(
            String sourceRefType,
            UUID sourceChunkId,
            UUID repoScanId,
            String filePath,
            String claimType,
            String subject,
            String capability,
            String action,
            String technology,
            String projectName,
            String resultText,
            String sourceType,
            String title,
            String sourceUri,
            String headingPath,
            String quote,
            String strength,
            String riskLevel,
            double confidence,
            String extractionMethod,
            Map<String, Object> metadata
    ) {
        this(
                sourceRefType,
                sourceChunkId,
                repoScanId,
                filePath,
                claimType,
                subject,
                capability,
                action,
                technology,
                projectName,
                resultText,
                sourceType,
                title,
                sourceUri,
                headingPath,
                quote,
                strength,
                riskLevel,
                confidence,
                "VALID",
                "UNKNOWN",
                extractionMethod,
                metadata
        );
    }
}
