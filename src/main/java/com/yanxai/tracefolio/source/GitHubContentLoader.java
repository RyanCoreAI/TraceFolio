package com.yanxai.tracefolio.source;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class GitHubContentLoader {

    private final RestClient restClient;

    public GitHubContentLoader(TraceFolioProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "TraceFolioBot/0.1");

        String token = properties.github().token();
        if (token != null && !token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }

        this.restClient = builder.build();
    }

    public RawDocument loadReadme(String owner, String repo) {
        return loadFile(owner, repo, "README.md", "GITHUB_README");
    }

    public RawDocument loadFile(String owner, String repo, String path, String sourceType) {
        GitHubContentResponse response = restClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .retrieve()
                .body(GitHubContentResponse.class);

        if (response == null || response.content() == null || response.content().isBlank()) {
            throw new IllegalStateException("GitHub file not found: " + owner + "/" + repo + "/" + path);
        }

        String content = decodeBase64(response.content());
        String repoFullName = owner + "/" + repo;
        String title = repoFullName + " " + path;

        return new RawDocument(
                UUID.randomUUID(),
                sourceType,
                title,
                response.html_url(),
                content,
                Map.of(
                        "owner", owner,
                        "repo", repo,
                        "path", path,
                        "sha", nullToBlank(response.sha()),
                        "htmlUrl", nullToBlank(response.html_url())
                )
        );
    }

    private static String decodeBase64(String content) {
        String normalized = content.replace("\n", "").replace("\r", "");
        byte[] bytes = Base64.getDecoder().decode(normalized);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    public record GitHubContentResponse(
            String name,
            String path,
            String sha,
            String content,
            String encoding,
            String html_url,
            String download_url
    ) {
    }
}
