package com.yanxai.tracefolio.repo;

import java.util.List;

public record RepoScanRequest(
        String source,
        String owner,
        String repo,
        String branch,
        String localPath,
        Boolean confirmOwnership,
        Integer maxFiles,
        String scanMode,
        List<String> includePaths,
        List<String> excludePaths,
        String commitSha,
        Integer maxFileChars
) {
    public RepoScanRequest(
            String source,
            String owner,
            String repo,
            String branch,
            String localPath,
            Boolean confirmOwnership,
            Integer maxFiles
    ) {
        this(source, owner, repo, branch, localPath, confirmOwnership, maxFiles, null, List.of(), List.of(), null, null);
    }

    public String safeSource() {
        if (source == null || source.isBlank()) {
            return localPath == null || localPath.isBlank() ? "GITHUB" : "LOCAL";
        }
        return source.strip().toUpperCase();
    }

    public boolean ownershipConfirmed() {
        return Boolean.TRUE.equals(confirmOwnership);
    }

    public int safeMaxFiles() {
        if (maxFiles == null || maxFiles <= 0) {
            return 28;
        }
        return Math.min(maxFiles, 60);
    }

    public String safeScanMode() {
        if (scanMode == null || scanMode.isBlank()) {
            return "FAST";
        }
        String value = scanMode.strip().toUpperCase();
        return "DEEP".equals(value) ? "DEEP" : "FAST";
    }

    public List<String> includePathsOrEmpty() {
        return cleanPaths(includePaths);
    }

    public List<String> excludePathsOrEmpty() {
        return cleanPaths(excludePaths);
    }

    public int safeMaxFileChars() {
        if (maxFileChars == null || maxFileChars <= 0) {
            return "DEEP".equals(safeScanMode()) ? 200_000 : 120_000;
        }
        return Math.min(maxFileChars, 300_000);
    }

    public String safeCommitSha() {
        return commitSha == null || commitSha.isBlank() ? null : commitSha.strip();
    }

    private List<String> cleanPaths(List<String> paths) {
        if (paths == null) {
            return List.of();
        }
        return paths.stream()
                .filter(path -> path != null && !path.isBlank())
                .map(path -> path.replace('\\', '/').strip())
                .distinct()
                .limit(40)
                .toList();
    }
}
