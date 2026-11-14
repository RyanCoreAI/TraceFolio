package com.yanxai.tracefolio.source;

import jakarta.validation.constraints.NotBlank;

public record GitHubIngestRequest(
        @NotBlank String owner,
        @NotBlank String repo,
        String path
) {
    public String pathOrReadme() {
        return path == null || path.isBlank() ? "README.md" : path;
    }
}
