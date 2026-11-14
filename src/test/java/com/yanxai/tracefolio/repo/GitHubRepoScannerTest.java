package com.yanxai.tracefolio.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.util.List;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.Test;

class GitHubRepoScannerTest {

    @Test
    void resolvesSlashBranchOnceAndPinsTreeSnapshotAndEncodedEvidenceLinks() {
        assertPinnedScan(null, "feature%2Fproof");
    }

    @Test
    void resolvesRequestedCommitAndDoesNotUseDisplayBranchForEvidence() {
        assertPinnedScan("abcdef1", "abcdef1");
    }

    private void assertPinnedScan(String requestedCommit, String encodedRef) {
        String sha = "abcdef1234567890abcdef1234567890abcdef12";
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RepoScanRepository repository = mock(RepoScanRepository.class);
        when(repository.save(any())).thenReturn(UUID.randomUUID());
        GitHubRepoScanner scanner = new GitHubRepoScanner(
                new TraceFolioProperties("tenant", null, null, null, null, null),
                repository, mock(RepoEvidenceClaimWriter.class), builder);
        server.expect(requestTo("https://api.github.com/repos/owner/repo"))
                .andRespond(withSuccess("{\"default_branch\":\"feature/proof\",\"html_url\":\"https://github.com/owner/repo\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/commits/" + encodedRef))
                .andRespond(withSuccess("{\"sha\":\"" + sha + "\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/trees/" + sha + "?recursive=1"))
                .andRespond(withSuccess("{\"tree\":[{\"path\":\"docs/证据 #1.md\",\"type\":\"blob\",\"sha\":\"blob-sha\",\"size\":30}],\"truncated\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/git/blobs/blob-sha"))
                .andRespond(withSuccess("{\"encoding\":\"base64\",\"content\":\""
                        + Base64.getEncoder().encodeToString("spring-boot".getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        + "\"}", MediaType.APPLICATION_JSON));
        RepoScanResponse response = scanner.scan(new RepoScanRequest("GITHUB", "owner", "repo", null, null,
                false, 10, null, List.of(), List.of(), requestedCommit, null));
        assertThat(response.commitSha()).isEqualTo(sha);
        assertThat(response.snapshot()).containsEntry("commitSha", sha);
        assertThat(response.evidence()).isNotEmpty().allSatisfy(item ->
                assertThat(item.sourceUri()).isEqualTo("https://github.com/owner/repo/blob/" + sha
                        + "/docs/%E8%AF%81%E6%8D%AE%20%231.md#L1"));
        server.verify();
    }

    @Test
    void refusesToSaveEvidenceIfGitHubDoesNotResolveAnImmutableCommit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RepoScanRepository repository = mock(RepoScanRepository.class);
        RepoEvidenceClaimWriter writer = mock(RepoEvidenceClaimWriter.class);
        GitHubRepoScanner scanner = new GitHubRepoScanner(
                new TraceFolioProperties("tenant", null, null, null, null, null), repository, writer, builder);
        server.expect(requestTo("https://api.github.com/repos/owner/repo"))
                .andRespond(withSuccess("{\"default_branch\":\"main\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.github.com/repos/owner/repo/commits/main"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> scanner.scan(new RepoScanRequest("GITHUB", "owner", "repo", null, null, false, 10)))
                .hasMessageContaining("完整 commit SHA");
        verifyNoInteractions(repository, writer);
        server.verify();
    }

    @Test
    void extractsRepoEvidenceWithLineAnchor() {
        TraceFolioProperties properties = new TraceFolioProperties(
                "ryan",
                null,
                null,
                new TraceFolioProperties.GitHub(""),
                null,
                null
        );
        GitHubRepoScanner scanner = new GitHubRepoScanner(properties, null, null);

        List<RepoEvidenceItem> evidence = scanner.analyzeFile(
                "RyanCoreAI",
                "TraceFolio",
                "main",
                "pom.xml",
                """
                <project>
                  <artifactId>spring-boot-starter-web</artifactId>
                  <artifactId>spring-ai-starter-vector-store-pgvector</artifactId>
                </project>
                """
        );

        assertThat(evidence).isNotEmpty();
        assertThat(evidence)
                .anySatisfy(item -> {
                    assertThat(item.technology()).isEqualTo("Spring Boot");
                    assertThat(item.lineNumber()).isEqualTo(2);
                    assertThat(item.sourceUri()).endsWith("pom.xml#L2");
                    assertThat(item.quote()).contains("spring-boot-starter-web");
                });
        assertThat(evidence)
                .extracting(RepoEvidenceItem::technology)
                .contains("PGVector");
    }

    @Test
    void canBuildLocalEvidenceLinksWithoutLeakingFilesystemPath() {
        TraceFolioProperties properties = new TraceFolioProperties(
                "ryan",
                null,
                null,
                new TraceFolioProperties.GitHub(""),
                null,
                null
        );
        GitHubRepoScanner scanner = new GitHubRepoScanner(properties, null, null);

        List<RepoEvidenceItem> evidence = scanner.analyzeFile(
                "local",
                "TraceFolio",
                "local",
                "src/main/java/AppController.java",
                "@RestController class AppController {}",
                "LOCAL"
        );

        assertThat(evidence)
                .anySatisfy(item -> {
                    assertThat(item.sourceUri()).isEqualTo("local://local/TraceFolio/src/main/java/AppController.java#L1");
                    assertThat(item.sourceUri()).doesNotContain("D:");
                });
    }
}
