package com.yanxai.tracefolio.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;

class LocalRepoBoundaryTest {
    @TempDir Path directory;
    private final GitHubRepoScanner analyzer = mock(GitHubRepoScanner.class);
    private final RepoScanRepository repository = mock(RepoScanRepository.class);
    private final RepoEvidenceClaimWriter writer = mock(RepoEvidenceClaimWriter.class);

    @Test
    void scansAllowedRootAndBoundsOversizedReads() throws Exception {
        Path root = Files.createDirectory(directory.resolve("allowed"));
        Files.writeString(root.resolve("README.md"), "Synthetic evidence.");
        Files.writeString(root.resolve("CHANGELOG.md"), "x".repeat(20_000));
        var scanner = new LocalRepoScanner(analyzer, repository, writer, root.toString());
        var request = new RepoScanRequest("LOCAL", "test", "repo", null, root.toString(), true, 10,
                "FAST", List.of(), List.of(), null, 100);
        var response = scanner.scan(request);
        assertThat(response.scannedFiles()).isEqualTo(1);
        assertThat(response.selectedFiles()).containsExactly("README.md");
        assertThat(response.warnings()).anyMatch(warning -> warning.contains("过大的文件"));
        verify(analyzer).analyzeFile("test", "repo", "local", "README.md", "Synthetic evidence.", "LOCAL");
    }

    @Test
    void siblingWithSimilarNameIsNotAnAllowedRoot() throws Exception {
        Path root = Files.createDirectory(directory.resolve("allowed"));
        Path outside = Files.createDirectory(directory.resolve("allowed-other"));
        var scanner = new LocalRepoScanner(analyzer, repository, writer, root.toString());
        assertThatThrownBy(() -> scanner.scan(new RepoScanRequest("LOCAL", "test", "repo", null, outside.toString(), true, 10)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(analyzer, repository, writer);
    }

    @Test
    void directoryExcludesDoNotExcludeSimilarlyNamedFiles() throws Exception {
        Path root = Files.createDirectory(directory.resolve("allowed"));
        Files.createDirectory(root.resolve("docs"));
        Files.writeString(root.resolve("docs/secret.md"), "Excluded.");
        Files.writeString(root.resolve("docs-public.md"), "Included.");
        var scanner = new LocalRepoScanner(analyzer, repository, writer, root.toString());
        var response = scanner.scan(new RepoScanRequest("LOCAL", "test", "repo", null, root.toString(), true, 10,
                "DEEP", List.of(), List.of("docs"), null, 100));
        assertThat(response.selectedFiles()).containsExactly("docs-public.md");
    }
}
