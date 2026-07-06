package com.yanxai.tracefolio.gaps;

import java.util.List;

public record GapInput(
        String requirementId,
        String description,
        String category,
        List<String> keywords
) {
    public List<String> keywordsOrEmpty() {
        return keywords == null ? List.of() : keywords;
    }
}
