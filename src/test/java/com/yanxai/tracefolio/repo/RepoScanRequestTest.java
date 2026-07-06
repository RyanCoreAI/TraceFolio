package com.yanxai.tracefolio.repo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RepoScanRequestTest {

    @Test
    void capsMaxFilesForReadOnlyScan() {
        assertThat(new RepoScanRequest("GITHUB", "RyanCoreAI", "TraceFolio", null, null, null, null).safeMaxFiles()).isEqualTo(28);
        assertThat(new RepoScanRequest("GITHUB", "RyanCoreAI", "TraceFolio", null, null, null, 500).safeMaxFiles()).isEqualTo(60);
        assertThat(new RepoScanRequest("GITHUB", "RyanCoreAI", "TraceFolio", null, null, null, 3).safeMaxFiles()).isEqualTo(3);
    }

    @Test
    void infersLocalSourceWhenLocalPathIsProvided() {
        RepoScanRequest request = new RepoScanRequest(null, null, null, null, "D:\\TraceFolio", true, null);

        assertThat(request.safeSource()).isEqualTo("LOCAL");
        assertThat(request.ownershipConfirmed()).isTrue();
    }

    @Test
    void supportsV2ScanOptionsWithConservativeCaps() {
        RepoScanRequest request = new RepoScanRequest(
                "github",
                "RyanCoreAI",
                "TraceFolio",
                null,
                null,
                null,
                100,
                "deep",
                java.util.List.of("src/main"),
                java.util.List.of("target", "node_modules"),
                "abc123",
                999_999
        );

        assertThat(request.safeSource()).isEqualTo("GITHUB");
        assertThat(request.safeScanMode()).isEqualTo("DEEP");
        assertThat(request.safeMaxFiles()).isEqualTo(60);
        assertThat(request.safeMaxFileChars()).isEqualTo(300_000);
        assertThat(request.includePathsOrEmpty()).containsExactly("src/main");
        assertThat(request.excludePathsOrEmpty()).containsExactly("target", "node_modules");
        assertThat(request.safeCommitSha()).isEqualTo("abc123");
    }
}
