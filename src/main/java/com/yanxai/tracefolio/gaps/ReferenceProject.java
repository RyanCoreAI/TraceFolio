package com.yanxai.tracefolio.gaps;

public record ReferenceProject(
        String fullName,
        String url,
        String description,
        Integer stars,
        String language,
        String license,
        String updatedAt
) {
}
