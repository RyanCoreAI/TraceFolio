package com.yanxai.tracefolio.gaps;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class GitHubProjectScout {

    private final TraceFolioProperties properties;
    private final RestClient restClient;

    public GitHubProjectScout(TraceFolioProperties properties) {
        this.properties = properties;
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("https://api.github.com")
                .defaultHeader(HttpHeaders.USER_AGENT, "TraceFolio-JD-Evidence-Compiler");
        if (properties.github().token() != null && !properties.github().token().isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.github().token());
        }
        this.restClient = builder.build();
    }

    public List<GapProjectPlan> planProjects(List<GapInput> gaps, int targetDays) {
        return gaps.stream()
                .limit(5)
                .map(gap -> plan(gap, Math.max(3, Math.min(targetDays, 7))))
                .toList();
    }

    public List<GapInput> gapsFromMatrix(List<EvidenceMatrixRow> matrix) {
        if (matrix == null) {
            return List.of();
        }
        return matrix.stream()
                .filter(row -> "GAP_PROJECT_REQUIRED".equals(row.supportStatus()))
                .map(row -> new GapInput(row.requirementId(), row.requirement(), row.category(), inferKeywords(row.requirement())))
                .toList();
    }

    private GapProjectPlan plan(GapInput gap, int targetDays) {
        String query = buildSearchQuery(gap);
        ScoutResult scout = scout(query);
        String capability = gap.description();
        String projectName = projectName(gap);
        return new GapProjectPlan(
                gap.requirementId(),
                capability,
                query,
                scout.projects(),
                projectName,
                "%d 天内做出一个能证明“%s”的最小闭环，而不是复制参考项目。".formatted(targetDays, capability),
                scope(gap),
                acceptanceCriteria(gap),
                readmeEvidence(gap),
                "完成后可写：在个人项目“%s”中实现/验证了“%s”的最小闭环，并提供 README、运行方式和关键代码说明。".formatted(projectName, capability),
                "仍然不能写：企业生产级落地、千万级流量、正式工作职责、商业收入或大厂经历，除非后续有独立证据支撑。",
                scout.warning()
        );
    }

    private ScoutResult scout(String query) {
        try {
            String uri = UriComponentsBuilder.fromPath("/search/repositories")
                    .queryParam("q", query)
                    .queryParam("sort", "stars")
                    .queryParam("order", "desc")
                    .queryParam("per_page", 3)
                    .build()
                    .toUriString();
            GitHubSearchResponse response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(GitHubSearchResponse.class);
            if (response == null || response.items() == null) {
                return new ScoutResult(List.of(), null);
            }
            List<ReferenceProject> projects = response.items().stream()
                    .map(item -> new ReferenceProject(
                            item.full_name(),
                            item.html_url(),
                            item.description(),
                            item.stargazers_count(),
                            item.language(),
                            item.license() == null ? null : item.license().spdx_id(),
                            item.updated_at()
                    ))
                    .toList();
            return new ScoutResult(projects, null);
        } catch (Exception e) {
            return new ScoutResult(List.of(), "GitHub 搜索暂不可用，只生成本地项目闭环计划：" + e.getMessage());
        }
    }

    private String buildSearchQuery(GapInput gap) {
        List<String> terms = new ArrayList<>(gap.keywordsOrEmpty());
        if (terms.isEmpty()) {
            terms.addAll(inferKeywords(gap.description()));
        }
        String text = String.join(" ", terms).strip();
        if (text.isBlank()) {
            text = gap.description();
        }
        String pushedSince = LocalDate.now().minusYears(1).toString();
        return "%s language:Java stars:>50 pushed:>%s archived:false".formatted(text, pushedSince);
    }

    private List<String> inferKeywords(String description) {
        String lower = description == null ? "" : description.toLowerCase(Locale.ROOT);
        List<String> terms = new ArrayList<>();
        List<String> candidates = List.of(
                "Spring Boot", "Spring AI", "RAG", "PGVector", "PostgreSQL", "Redis", "JWT",
                "Docker", "CI/CD", "Observability", "Agent", "Tool Calling", "LLM", "Embedding",
                "Java", "Vue", "React", "微服务", "高并发", "权限", "部署", "测试"
        );
        for (String candidate : candidates) {
            if (lower.contains(candidate.toLowerCase(Locale.ROOT))) {
                terms.add(candidate);
            }
        }
        return terms.stream().distinct().limit(5).toList();
    }

    private List<String> scope(GapInput gap) {
        String capability = gap.description();
        return List.of(
                "搭建一个最小 Spring Boot API，功能只覆盖“%s”的核心路径。".formatted(capability),
                "写入 README：问题背景、技术选型、运行方式、关键接口、边界和不支持事项。",
                "保留 2-3 个可运行 smoke command，证明不是纯文档项目。",
                "补一篇短复盘：做了什么、没做什么、下一步如何扩展。"
        );
    }

    private List<String> acceptanceCriteria(GapInput gap) {
        return List.of(
                "本地一条命令或三步以内可以跑通。",
                "至少一个 API 或 CLI 示例能展示核心能力。",
                "README 中出现可引用的设计说明和验收截图/命令输出。",
                "不能把 demo 描述成生产级系统。"
        );
    }

    private List<String> readmeEvidence(GapInput gap) {
        return List.of(
                "能力声明：这个项目证明了哪条 JD 要求。",
                "实现证据：关键代码路径、接口、配置和运行命令。",
                "边界说明：哪些能力没有做，不能如何夸大。",
                "复盘：遇到的问题、取舍和后续增强路线。"
        );
    }

    private String projectName(GapInput gap) {
        String normalized = gap.description() == null ? "Gap" : gap.description()
                .replaceAll("[^A-Za-z0-9\\u4e00-\\u9fa5]+", " ")
                .strip();
        if (normalized.isBlank()) {
            return "TraceFolio Gap Lab";
        }
        String[] parts = normalized.split("\\s+");
        String first = parts.length == 0 ? "Gap" : parts[0];
        return "TraceFolio " + first + " Lab";
    }

    private record ScoutResult(List<ReferenceProject> projects, String warning) {
    }

    private record GitHubSearchResponse(List<GitHubRepo> items) {
    }

    private record GitHubRepo(
            String full_name,
            String html_url,
            String description,
            Integer stargazers_count,
            String language,
            GitHubLicense license,
            String updated_at
    ) {
    }

    private record GitHubLicense(String spdx_id) {
    }
}
