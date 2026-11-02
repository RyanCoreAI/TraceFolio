package com.yanxai.tracefolio.repo;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GitHubRepoScanner {

    private final RestClient restClient;
    private final RepoScanRepository repository;
    private final RepoEvidenceClaimWriter claimWriter;

    @Autowired
    public GitHubRepoScanner(
            TraceFolioProperties properties,
            RepoScanRepository repository,
            RepoEvidenceClaimWriter claimWriter
    ) {
        this(properties, repository, claimWriter, RestClient.builder());
    }

    GitHubRepoScanner(
            TraceFolioProperties properties,
            RepoScanRepository repository,
            RepoEvidenceClaimWriter claimWriter,
            RestClient.Builder builder
    ) {
        this.repository = repository;
        this.claimWriter = claimWriter;
        builder
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader("X-GitHub-Api-Version", "2022-11-28")
                .defaultHeader(HttpHeaders.USER_AGENT, "TraceFolio-Repo-Evidence-Scanner");
        if (properties.github().token() != null && !properties.github().token().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.github().token());
        }
        this.restClient = builder.build();
    }

    public RepoScanResponse scan(RepoScanRequest request) {
        if (request.owner() == null || request.owner().isBlank() || request.repo() == null || request.repo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GitHub 仓库扫描需要 owner 和 repo。");
        }
        GitHubRepoMetadata metadata = restClient.get()
                .uri("/repos/{owner}/{repo}", request.owner(), request.repo())
                .retrieve()
                .body(GitHubRepoMetadata.class);
        String branch = firstNonBlank(request.branch(), metadata == null ? null : metadata.default_branch(), "main");
        String requestedRef = firstNonBlank(request.safeCommitSha(), branch, "main");
        GitHubCommitResponse commit = restClient.get()
                .uri("/repos/{owner}/{repo}/commits/{ref}", request.owner(), request.repo(), requestedRef)
                .retrieve()
                .body(GitHubCommitResponse.class);
        if (commit == null || commit.sha() == null || !commit.sha().matches("[0-9a-fA-F]{40}")) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub 未返回可固定的完整 commit SHA，未保存扫描证据。");
        }
        String commitSha = commit.sha().toLowerCase(Locale.ROOT);
        String sourceUri = metadata == null || metadata.html_url() == null
                ? "https://github.com/%s/%s".formatted(request.owner(), request.repo())
                : metadata.html_url();

        GitHubTreeResponse tree = restClient.get()
                .uri(builder -> builder.path("/repos/{owner}/{repo}/git/trees/{branch}")
                        .queryParam("recursive", "1")
                        .build(request.owner(), request.repo(), commitSha))
                .retrieve()
                .body(GitHubTreeResponse.class);

        List<GitHubTreeItem> selected = selectFiles(tree, request);
        List<RepoEvidenceItem> evidence = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (tree != null && tree.truncated()) {
            warnings.add("GitHub tree API 返回 truncated=true，本次扫描不是全仓完整视图。");
        }
        for (GitHubTreeItem item : selected) {
            String content = fetchBlob(request.owner(), request.repo(), item.sha());
            if (content.length() > request.safeMaxFileChars()) {
                warnings.add("跳过过大的文件：" + item.path());
                continue;
            }
            evidence.addAll(analyzeFile(
                    request.owner(),
                    request.repo(),
                    commitSha,
                    item.path(),
                    content,
                    "GITHUB"
            ));
        }

        warnings.add("Repo Evidence Extractor 只读扫描仓库，不会修改代码，也不会把参考项目当作你的经历。");
        warnings.add("GitHub stars 只代表热度，不能作为代码质量或简历价值证明。");

        Map<String, Integer> summary = summarize(evidence);
        RepoScanResponse response = new RepoScanResponse(
                UUID.randomUUID(),
                request.owner(),
                request.repo(),
                branch,
                sourceUri,
                request.safeScanMode(),
                commitSha,
                selected.size(),
                0,
                summary,
                evidence.stream().limit(80).toList(),
                warnings,
                selected.stream().map(GitHubTreeItem::path).toList(),
                snapshot(tree, selected, request, commitSha)
        );
        UUID scanId = repository.save(response);
        int insertedClaims = claimWriter.insertClaims(scanId, request.owner(), request.repo(), branch, "GITHUB_REPO_FILE", response.evidence());
        return new RepoScanResponse(
                scanId,
                response.owner(),
                response.repo(),
                response.branch(),
                response.sourceUri(),
                response.scanMode(),
                response.commitSha(),
                response.scannedFiles(),
                insertedClaims,
                response.evidenceSummary(),
                response.evidence(),
                response.warnings(),
                response.selectedFiles(),
                response.snapshot()
        );
    }

    private List<GitHubTreeItem> selectFiles(GitHubTreeResponse tree, RepoScanRequest request) {
        if (tree == null || tree.tree() == null) {
            return List.of();
        }
        return tree.tree().stream()
                .filter(item -> "blob".equals(item.type()))
                .filter(item -> selectedByRequest(item.path(), request))
                .sorted(Comparator.comparingInt((GitHubTreeItem item) -> priority(item.path()))
                        .thenComparing(GitHubTreeItem::path))
                .limit(request.safeMaxFiles())
                .toList();
    }

    private boolean selectedByRequest(String path, RepoScanRequest request) {
        String normalized = path.toLowerCase(Locale.ROOT);
        boolean excluded = request.excludePathsOrEmpty().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::startsWith);
        if (excluded) {
            return false;
        }
        boolean included = request.includePathsOrEmpty().stream()
                .map(item -> item.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::startsWith);
        return included || interestingPath(path) || ("DEEP".equals(request.safeScanMode()) && deepPath(path));
    }

    private boolean interestingPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.equals("readme.md")
                || lower.equals("changelog.md")
                || lower.startsWith("docs/")
                || lower.equals("pom.xml")
                || lower.equals("build.gradle")
                || lower.equals("build.gradle.kts")
                || lower.equals("package.json")
                || lower.equals("docker-compose.yml")
                || lower.equals("docker-compose.yaml")
                || lower.equals("dockerfile")
                || lower.startsWith(".github/workflows/")
                || lower.contains("/db/migration/")
                || lower.endsWith("controller.java")
                || lower.endsWith("service.java")
                || lower.endsWith("repository.java")
                || lower.contains("/src/test/")
                || lower.contains("__tests__");
    }

    private boolean deepPath(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".java")
                || lower.endsWith(".kt")
                || lower.endsWith(".js")
                || lower.endsWith(".ts")
                || lower.endsWith(".vue")
                || lower.endsWith(".md")
                || lower.endsWith(".yml")
                || lower.endsWith(".yaml")
                || lower.endsWith(".json")
                || lower.endsWith(".sql");
    }

    private int priority(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("readme.md")) {
            return 0;
        }
        if (lower.equals("pom.xml") || lower.equals("package.json")) {
            return 1;
        }
        if (lower.equals("docker-compose.yml") || lower.equals("dockerfile") || lower.startsWith(".github/workflows/")) {
            return 2;
        }
        if (lower.endsWith("controller.java") || lower.endsWith("service.java")) {
            return 3;
        }
        if (lower.contains("/db/migration/") || lower.contains("/src/test/")) {
            return 4;
        }
        return 8;
    }

    private String fetchBlob(String owner, String repo, String sha) {
        GitHubBlobResponse blob = restClient.get()
                .uri("/repos/{owner}/{repo}/git/blobs/{sha}", owner, repo, sha)
                .retrieve()
                .body(GitHubBlobResponse.class);
        if (blob == null || blob.content() == null) {
            return "";
        }
        if (!"base64".equalsIgnoreCase(blob.encoding())) {
            return blob.content();
        }
        String normalized = blob.content().replace("\n", "").replace("\r", "");
        return new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
    }

    List<RepoEvidenceItem> analyzeFile(String owner, String repo, String branch, String path, String content) {
        return analyzeFile(owner, repo, branch, path, content, "GITHUB");
    }

    List<RepoEvidenceItem> analyzeFile(String owner, String repo, String branch, String path, String content, String linkMode) {
        String lower = (path + "\n" + content).toLowerCase(Locale.ROOT);
        List<RepoEvidenceItem> items = new ArrayList<>();
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "Spring Boot 后端", "Spring Boot", List.of("spring-boot", "@restcontroller", "springapplication"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "Spring AI 能力", "Spring AI", List.of("spring-ai", "spring ai"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "可信 RAG", "RAG", List.of("rag", "retrieval", "citation", "abstain", "rrf"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "向量检索", "PGVector", List.of("pgvector", "vector(", "embedding"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "PostgreSQL 数据建模", "PostgreSQL", List.of("postgresql", "tsvector", "jsonb", "flyway"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "Docker 本地运行", "Docker", List.of("docker", "docker compose", "docker-compose"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "CI/CD 自动化", "GitHub Actions", List.of("github actions", "actions/checkout", "mvn test", "npm run build"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "Vue 前端工作台", "Vue", List.of("\"vue\"", "vite", "@lucide/vue"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "测试能力", "Tests", List.of("@test", "assertthat", "junit", "vitest"));
        addIfSignal(items, owner, repo, branch, path, content, lower, linkMode, "安全边界", "Security", List.of("jwt", "auth", "permission", "csrf", "cors"));
        return items;
    }

    private void addIfSignal(
            List<RepoEvidenceItem> items,
            String owner,
            String repo,
            String branch,
            String path,
            String content,
            String lower,
            String linkMode,
            String capability,
            String technology,
            List<String> signals
    ) {
        List<String> matched = signals.stream()
                .filter(signal -> lower.contains(signal.toLowerCase(Locale.ROOT)))
                .distinct()
                .toList();
        if (matched.isEmpty()) {
            return;
        }
        QuoteMatch quote = quoteLine(content, matched);
        String quoteText = quote.text();
        if (quoteText.isBlank()) {
            quoteText = path;
        }
        String sourceUri = sourceUri(owner, repo, branch, path, quote.lineNumber(), linkMode);
        items.add(new RepoEvidenceItem(
                capability,
                technology,
                path,
                quote.lineNumber(),
                sourceUri,
                quoteText,
                strength(path),
                riskLevel(technology),
                matched,
                fileHash(content),
                symbolType(path),
                symbolName(path, content),
                sourceKind(path)
        ));
    }

    private Map<String, Object> snapshot(GitHubTreeResponse tree, List<GitHubTreeItem> selected, RepoScanRequest request, String commitSha) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("scanMode", request.safeScanMode());
        snapshot.put("commitSha", commitSha);
        snapshot.put("includePaths", request.includePathsOrEmpty());
        snapshot.put("excludePaths", request.excludePathsOrEmpty());
        snapshot.put("maxFiles", request.safeMaxFiles());
        snapshot.put("maxFileChars", request.safeMaxFileChars());
        snapshot.put("treeTruncated", tree != null && tree.truncated());
        snapshot.put("files", selected.stream()
                .map(item -> Map.of(
                        "path", item.path(),
                        "sha", item.sha() == null ? "" : item.sha(),
                        "size", item.size() == null ? 0 : item.size()
                ))
                .toList());
        return snapshot;
    }

    private String fileHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    private String symbolType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith("controller.java")) {
            return "CONTROLLER";
        }
        if (lower.endsWith("service.java")) {
            return "SERVICE";
        }
        if (lower.endsWith("repository.java")) {
            return "REPOSITORY";
        }
        if (lower.contains("/src/test/")) {
            return "TEST";
        }
        if (lower.contains("/db/migration/") || lower.endsWith(".sql")) {
            return "MIGRATION";
        }
        if (lower.equals("pom.xml") || lower.equals("package.json")) {
            return "MANIFEST";
        }
        if (lower.startsWith(".github/workflows/")) {
            return "CI_WORKFLOW";
        }
        if (lower.endsWith(".vue") || lower.endsWith(".js") || lower.endsWith(".ts")) {
            return "FRONTEND";
        }
        if (lower.endsWith(".md")) {
            return "DOC";
        }
        return "FILE";
    }

    private String symbolName(String path, String content) {
        String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (content == null || content.isBlank()) {
            return fileName;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(class|interface|record|enum)\\s+([A-Za-z0-9_]+)")
                .matcher(content);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return fileName;
    }

    private String sourceKind(String path) {
        String type = symbolType(path);
        if (List.of("CONTROLLER", "SERVICE", "REPOSITORY", "TEST", "MIGRATION", "CI_WORKFLOW", "MANIFEST").contains(type)) {
            return "IMPLEMENTATION";
        }
        if ("DOC".equals(type)) {
            return "DOCUMENTATION";
        }
        return "FILE";
    }

    private String sourceUri(String owner, String repo, String branch, String path, Integer lineNumber, String linkMode) {
        if ("LOCAL".equalsIgnoreCase(linkMode)) {
            String base = "local://%s/%s/%s".formatted(owner, repo, path);
            return lineNumber == null ? base : base + "#L" + lineNumber;
        }
        String base = UriComponentsBuilder.fromUriString("https://github.com")
                .pathSegment(owner, repo, "blob", branch)
                .pathSegment(path.split("/"))
                .build().encode().toUriString();
        return lineNumber == null ? base : base + "#L" + lineNumber;
    }

    private QuoteMatch quoteLine(String content, List<String> matched) {
        String[] lines = content.split("\\R");
        for (String signal : matched) {
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (line.toLowerCase(Locale.ROOT).contains(signal.toLowerCase(Locale.ROOT))) {
                    String stripped = line.strip();
                    return new QuoteMatch(stripped.length() > 240 ? stripped.substring(0, 240) : stripped, i + 1);
                }
            }
        }
        return new QuoteMatch("", null);
    }

    private String strength(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.equals("pom.xml") || lower.equals("package.json") || lower.endsWith("controller.java")
                || lower.endsWith("service.java") || lower.contains("/src/test/")
                || lower.startsWith(".github/workflows/") || lower.contains("/db/migration/")) {
            return "STRONG";
        }
        if (lower.equals("readme.md") || lower.startsWith("docs/")) {
            return "MEDIUM";
        }
        return "WEAK";
    }

    private String riskLevel(String technology) {
        if (List.of("Security", "GitHub Actions", "Docker").contains(technology)) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Map<String, Integer> summarize(List<RepoEvidenceItem> evidence) {
        Map<String, Integer> summary = new LinkedHashMap<>();
        for (RepoEvidenceItem item : evidence) {
            summary.merge(item.capability(), 1, Integer::sum);
        }
        return summary;
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private record GitHubRepoMetadata(String default_branch, String html_url) {
    }

    private record GitHubCommitResponse(String sha) {
    }

    private record GitHubTreeResponse(List<GitHubTreeItem> tree, boolean truncated) {
    }

    private record GitHubTreeItem(String path, String type, String sha, Long size) {
    }

    private record GitHubBlobResponse(String content, String encoding) {
    }

    private record QuoteMatch(String text, Integer lineNumber) {
    }
}
