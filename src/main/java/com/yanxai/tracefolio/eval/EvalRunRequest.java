package com.yanxai.tracefolio.eval;

import java.util.List;

public record EvalRunRequest(
        String suite,
        List<String> tags,
        Boolean failFast
) {
    public String safeSuite() {
        return suite == null || suite.isBlank() ? "core" : suite.strip().toLowerCase();
    }

    public List<String> tagsOrEmpty() {
        return tags == null ? List.of() : tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::strip)
                .map(String::toLowerCase)
                .distinct()
                .toList();
    }

    public boolean shouldFailFast() {
        return Boolean.TRUE.equals(failFast);
    }
}
