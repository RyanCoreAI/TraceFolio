package com.yanxai.tracefolio.repo;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubRepoScannerTest {

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
