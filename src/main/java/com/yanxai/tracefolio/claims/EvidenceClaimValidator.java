package com.yanxai.tracefolio.claims;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EvidenceClaimValidator {

    private static final List<String> OVERCLAIM_TERMS = List.of(
            "生产级", "大规模", "千万", "百万用户", "商业化", "营收", "融资",
            "大厂", "正式工作", "企业采用", "主导上线"
    );

    public EvidenceClaimValidation validate(EvidenceClaimDraft claim) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (isBlank(claim.quote())) {
            errors.add("quote is required");
        }
        if (isBlank(claim.sourceUri())) {
            errors.add("sourceUri is required");
        }
        if ("CHUNK".equals(claim.sourceRefType()) && claim.sourceChunkId() == null) {
            errors.add("CHUNK claim requires sourceChunkId");
        }
        if ("REPO_SCAN".equals(claim.sourceRefType()) && claim.repoScanId() == null) {
            errors.add("REPO_SCAN claim requires repoScanId");
        }
        if (containsOverclaim(claim.action()) || containsOverclaim(claim.resultText()) || containsOverclaim(claim.capability())) {
            warnings.add("claim contains high-risk wording and must not be promoted without independent evidence");
        }
        if ("WEAK".equals(claim.strength()) && "HIGH".equals(claim.riskLevel())) {
            warnings.add("weak high-risk claim can only be used as refusal or gap evidence");
        }

        String status = errors.isEmpty()
                ? warnings.isEmpty() ? "VALID" : "NEEDS_REVIEW"
                : "INVALID";
        return new EvidenceClaimValidation(status, freshness(claim), errors, warnings);
    }

    public EvidenceClaimValidation validate(EvidenceClaim claim) {
        EvidenceClaimDraft draft = new EvidenceClaimDraft(
                claim.sourceRefType(),
                claim.sourceChunkId(),
                claim.repoScanId(),
                claim.filePath(),
                claim.claimType(),
                claim.subject(),
                claim.capability(),
                claim.action(),
                claim.technology(),
                claim.projectName(),
                claim.resultText(),
                claim.sourceType(),
                claim.title(),
                claim.sourceUri(),
                claim.headingPath(),
                claim.quote(),
                claim.strength(),
                claim.riskLevel(),
                claim.confidence(),
                claim.validationStatus(),
                claim.freshness(),
                claim.extractionMethod(),
                claim.metadata()
        );
        return validate(draft);
    }

    private boolean containsOverclaim(String value) {
        if (value == null) {
            return false;
        }
        return OVERCLAIM_TERMS.stream().anyMatch(value::contains);
    }

    private String freshness(EvidenceClaimDraft claim) {
        Object capturedAt = claim.metadata() == null ? null : claim.metadata().get("capturedAt");
        if (capturedAt == null) {
            return "UNKNOWN";
        }
        try {
            OffsetDateTime captured = OffsetDateTime.parse(capturedAt.toString());
            long days = ChronoUnit.DAYS.between(captured, OffsetDateTime.now());
            if (days <= 180) {
                return "FRESH";
            }
            if (days <= 730) {
                return "STALE";
            }
            return "OLD";
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
