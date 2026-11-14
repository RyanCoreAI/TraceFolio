package com.yanxai.tracefolio.jd;

import java.util.List;

public record EvidenceMatrixRow(
        String requirementId,
        String category,
        String requirement,
        String priority,
        String supportStatus,
        double confidence,
        String allowedResumeWording,
        String forbiddenWording,
        String scoreExplanation,
        String missingGap,
        List<MatchedEvidence> evidence
) {
    public EvidenceMatrixRow(
            String requirementId,
            String category,
            String requirement,
            String priority,
            String supportStatus,
            double confidence,
            String allowedResumeWording,
            String missingGap,
            List<MatchedEvidence> evidence
    ) {
        this(
                requirementId,
                category,
                requirement,
                priority,
                supportStatus,
                confidence,
                allowedResumeWording,
                defaultForbiddenWording(supportStatus),
                "",
                missingGap,
                evidence
        );
    }

    public List<MatchedEvidence> evidenceOrEmpty() {
        return evidence == null ? List.of() : evidence;
    }

    private static String defaultForbiddenWording(String supportStatus) {
        if ("SUPPORTED".equals(supportStatus)) {
            return "不能扩展成未被证据证明的生产规模、商业结果、正式工作职责或企业采用。";
        }
        if ("WEAKLY_SUPPORTED".equals(supportStatus)) {
            return "不能写成主导、生产级、大规模落地或商业化成果。";
        }
        return "不能写入简历正文，只能作为缺口或拒写项。";
    }
}
