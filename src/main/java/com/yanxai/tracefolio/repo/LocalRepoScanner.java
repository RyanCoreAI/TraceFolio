package com.yanxai.tracefolio.repo;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.file.FileVisitResult;
import java.nio.file.LinkOption;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocalRepoScanner {

    private static final int MAX_WALK_FILES = 15_000;

    private final GitHubRepoScanner analyzer;
    private final RepoScanRepository repository;
    private final RepoEvidenceClaimWriter claimWriter;
    private final List<Path> allowedRoots;

    public LocalRepoScanner(
            GitHubRepoScanner analyzer,
            RepoScanRepository repository,
            RepoEvidenceClaimWriter claimWriter
    ) {
        this(analyzer, repository, claimWriter, "");
    }

    @Autowired
    public LocalRepoScanner(GitHubRepoScanner analyzer, RepoScanRepository repository, RepoEvidenceClaimWriter claimWriter,
            @Value("${tracefolio.local-repo.allowed-roots:}") String allowedRoots) {
        this.analyzer = analyzer;
        this.repository = repository;
        this.claimWriter = claimWriter;
        this.allowedRoots = java.util.Arrays.stream(allowedRoots.split(","))
                .map(String::strip).filter(value -> !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize()).toList();
    }

    public RepoScanResponse scan(RepoScanRequest request) {
        if (!request.ownershipConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本地仓库扫描必须设置 confirmOwnership=true。");
        }
        if (request.localPath() == null || request.localPath().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本地仓库扫描需要 localPath。");
        }

        Path root;
        try {
            root = Path.of(request.localPath()).toRealPath();
            boolean allowed = false;
            for (Path configured : allowedRoots) {
                if (root.startsWith(configured.toRealPath())) {
                    allowed = true;
                    break;
                }
            }
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "本地扫描目录未获服务端授权；请配置 TRACEFOLIO_LOCAL_REPO_ALLOWED_ROOTS。");
            }
        } catch (IOException | java.nio.file.InvalidPathException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本地扫描目录配置无效。", e);
        }
        if (!Files.isDirectory(root)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "localPath 不是有效目录：" + root);
        }

        String repo = firstNonBlank(request.repo(), root.getFileName() == null ? "local-repo" : root.getFileName().toString());
        String owner = firstNonBlank(request.owner(), "local");
        String branch = firstNonBlank(request.branch(), "local");
        List<String> warnings = new ArrayList<>();
        if (!Files.isDirectory(root.resolve(".git"))) {
            warnings.add("未发现 .git 目录，本次按普通本地目录只读扫描。");
        }

        List<Path> selected = selectFiles(root, request, warnings);
        List<Path> processed = new ArrayList<>();
        Map<Path, String> hashes = new LinkedHashMap<>();
        List<RepoEvidenceItem> evidence = new ArrayList<>();
        for (Path file : selected) {
            String relativePath = normalizeRelative(root.relativize(file));
            try {
                if (!safeFile(root, file)) {
                    warnings.add("跳过符号链接或目录外文件：" + relativePath);
                    continue;
                }
                String content = readBounded(file, request.safeMaxFileChars());
                if (content.length() > request.safeMaxFileChars()) {
                    warnings.add("跳过过大的文件：" + relativePath);
                    continue;
                }
                evidence.addAll(analyzer.analyzeFile(owner, repo, branch, relativePath, content, "LOCAL"));
                processed.add(file);
                hashes.put(file, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8))));
            } catch (Exception e) {
                warnings.add("读取失败：" + relativePath + "，原因：" + e.getMessage());
            }
        }

        warnings.add("Local Repo Scanner 只读扫描用户确认拥有或可公开引用的仓库，不会修改文件。");
        Map<String, Integer> summary = summarize(evidence);
        RepoScanResponse draft = new RepoScanResponse(
                UUID.randomUUID(),
                owner,
                repo,
                branch,
                "local://%s/%s".formatted(owner, repo),
                request.safeScanMode(),
                commitSha(root),
                processed.size(),
                0,
                summary,
                evidence.stream().limit(120).toList(),
                warnings,
                processed.stream().map(path -> normalizeRelative(root.relativize(path))).toList(),
                snapshot(root, processed, request, warnings, hashes)
        );
        UUID scanId = repository.save(draft);
        int insertedClaims = claimWriter.insertClaims(scanId, owner, repo, branch, "LOCAL_REPO_FILE", draft.evidence());
        return new RepoScanResponse(
                scanId,
                draft.owner(),
                draft.repo(),
                draft.branch(),
                draft.sourceUri(),
                draft.scanMode(),
                draft.commitSha(),
                draft.scannedFiles(),
                insertedClaims,
                draft.evidenceSummary(),
                draft.evidence(),
                draft.warnings(),
                draft.selectedFiles(),
                draft.snapshot()
        );
    }

    private List<Path> selectFiles(Path root, RepoScanRequest request, List<String> warnings) {
        int maxFiles = request.safeMaxFiles();
        try {
            List<Path> found = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                private int visited;
                private boolean bounded() {
                    if (++visited <= MAX_WALK_FILES) return false;
                    warnings.add("已达到目录遍历上限，停止扫描。");
                    return true;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
                    if (bounded()) return FileVisitResult.TERMINATE;
                    if (!dir.toRealPath().startsWith(root) || Files.isSymbolicLink(dir)
                            || (!dir.equals(root) && (isSkipped(root, dir.resolve(".scan-marker"))
                            || excluded(normalizeRelative(root.relativize(dir)), request)))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    if (bounded()) return FileVisitResult.TERMINATE;
                    if (attributes.isRegularFile() && safeFile(root, file) && !isSkipped(root, file)
                            && selectedByRequest(normalizeRelative(root.relativize(file)), request)) found.add(file);
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException error) {
                    warnings.add("跳过无法访问的目录项：" + normalizeRelative(root.relativize(file)));
                    return bounded() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
                }
            });
            List<Path> candidates = found.stream().sorted(Comparator.comparingInt((Path path) -> priority(normalizeRelative(root.relativize(path))))
                    .thenComparing(path -> normalizeRelative(root.relativize(path)))).limit(maxFiles).toList();
            if (candidates.size() >= maxFiles) {
                warnings.add("已达到 maxFiles=%d，本次只扫描优先级最高的候选文件。".formatted(maxFiles));
            }
            return candidates;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "扫描本地目录失败：" + e.getMessage(), e);
        }
    }

    private boolean selectedByRequest(String relativePath, RepoScanRequest request) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        if (excluded(relativePath, request)) {
            return false;
        }
        boolean explicitlyIncluded = request.includePathsOrEmpty().stream()
                .map(path -> path.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::startsWith);
        return explicitlyIncluded || interestingPath(relativePath) || ("DEEP".equals(request.safeScanMode()) && deepPath(relativePath));
    }

    private boolean excluded(String relativePath, RepoScanRequest request) {
        String normalized = relativePath.toLowerCase(Locale.ROOT);
        return request.excludePathsOrEmpty().stream().map(path -> path.toLowerCase(Locale.ROOT).replaceAll("/+$", ""))
                .anyMatch(path -> normalized.equals(path) || normalized.startsWith(path + "/"));
    }

    private boolean safeFile(Path root, Path file) throws IOException {
        return !Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && file.toRealPath().startsWith(root);
    }

    private String readBounded(Path file, int maxChars) throws IOException {
        try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS);
             var reader = new InputStreamReader(input, StandardCharsets.UTF_8.newDecoder()
                     .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT))) {
            StringBuilder result = new StringBuilder(Math.min(maxChars + 1, 8192));
            char[] buffer = new char[4096];
            while (result.length() <= maxChars) {
                int read = reader.read(buffer, 0, Math.min(buffer.length, maxChars + 1 - result.length()));
                if (read < 0) break;
                result.append(buffer, 0, read);
            }
            return result.toString();
        }
    }

    private boolean isSkipped(Path root, Path path) {
        String relative = normalizeRelative(root.relativize(path)).toLowerCase(Locale.ROOT);
        return relative.startsWith(".git/")
                || relative.startsWith(".idea/")
                || relative.startsWith(".vscode/")
                || relative.startsWith("node_modules/")
                || relative.startsWith("target/")
                || relative.startsWith("build/")
                || relative.startsWith("dist/")
                || relative.startsWith(".gradle/")
                || relative.contains("/node_modules/")
                || relative.contains("/target/")
                || relative.contains("/build/")
                || relative.contains("/dist/");
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
                || lower.contains("__tests__")
                || lower.endsWith(".sql")
                || lower.endsWith(".http")
                || lower.endsWith(".ps1")
                || lower.endsWith(".sh");
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

    private Map<String, Object> snapshot(Path root, List<Path> selected, RepoScanRequest request, List<String> warnings, Map<Path, String> hashes) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("rootName", root.getFileName() == null ? root.toString() : root.getFileName().toString());
        snapshot.put("scanMode", request.safeScanMode());
        snapshot.put("includePaths", request.includePathsOrEmpty());
        snapshot.put("excludePaths", request.excludePathsOrEmpty());
        snapshot.put("maxFiles", request.safeMaxFiles());
        snapshot.put("maxFileChars", request.safeMaxFileChars());
        snapshot.put("warnings", warnings);
        snapshot.put("files", selected.stream()
                .map(path -> Map.of(
                        "path", normalizeRelative(root.relativize(path)),
                        "sha256", hashes.get(path)
                ))
                .toList());
        return snapshot;
    }

    private String commitSha(Path root) {
        try {
            Path head = root.resolve(".git/HEAD");
            if (!Files.exists(head) || !safeFile(root, head)) {
                return null;
            }
            String value = readBounded(head, 1024).strip();
            if (value.startsWith("ref:")) {
                String refName = value.substring(4).strip();
                if (!refName.matches("refs/[A-Za-z0-9_./-]+") || refName.contains("..")) return null;
                Path ref = root.resolve(".git").resolve(refName).normalize();
                if (Files.exists(ref) && safeFile(root, ref)) {
                    String hash = readBounded(ref, 128).strip();
                    return hash.matches("[a-fA-F0-9]{40,64}") ? hash : null;
                }
            }
            return value.matches("[a-fA-F0-9]{40,64}") ? value : null;
        } catch (Exception ignored) {
            return null;
        }
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
        if (lower.endsWith(".sql") || lower.endsWith(".http") || lower.endsWith(".ps1") || lower.endsWith(".sh")) {
            return 5;
        }
        return 8;
    }

    private Map<String, Integer> summarize(List<RepoEvidenceItem> evidence) {
        Map<String, Integer> summary = new java.util.LinkedHashMap<>();
        for (RepoEvidenceItem item : evidence) {
            summary.merge(item.capability(), 1, Integer::sum);
        }
        return summary;
    }

    private String normalizeRelative(Path path) {
        return path.toString().replace('\\', '/');
    }

    private String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first.strip();
    }
}
