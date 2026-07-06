package com.yanxai.tracefolio.jd;

import java.util.List;

public record JdRequirement(
        String id,
        String category,
        String description,
        String priority,
        String evidenceNeeded,
        String riskLevel,
        String requirementType,
        boolean hardGate,
        List<String> keywords
) {
    public JdRequirement(
            String id,
            String category,
            String description,
            String priority,
            String evidenceNeeded,
            String riskLevel,
            List<String> keywords
    ) {
        this(id, category, description, priority, evidenceNeeded, riskLevel, category, "HIGH".equals(riskLevel), keywords);
    }

    public List<String> keywordsOrEmpty() {
        return keywords == null ? List.of() : keywords;
    }
}
