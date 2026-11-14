package com.yanxai.tracefolio.portfolio;

import com.yanxai.tracefolio.claims.EvidenceClaim;
import com.yanxai.tracefolio.claims.EvidenceClaimRepository;
import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.JdRunRepository;
import com.yanxai.tracefolio.jd.JdRunView;
import com.yanxai.tracefolio.jd.MatchedEvidence;
import com.yanxai.tracefolio.resume.InterviewDefense;
import com.yanxai.tracefolio.resume.ProjectExperience;
import com.yanxai.tracefolio.resume.ResumeDraft;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioExportService {

    private final EvidenceClaimRepository claimRepository;
    private final JdRunRepository jdRunRepository;
    private final PortfolioExportRepository exportRepository;
    private final PortfolioPrivacyScanner privacyScanner;
    private final Parser markdownParser = Parser.builder().build();
    private final HtmlRenderer htmlRenderer = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();

    @Autowired
    public PortfolioExportService(
            EvidenceClaimRepository claimRepository,
            JdRunRepository jdRunRepository,
            PortfolioExportRepository exportRepository,
            PortfolioPrivacyScanner privacyScanner
    ) {
        this.claimRepository = claimRepository;
        this.jdRunRepository = jdRunRepository;
        this.exportRepository = exportRepository;
        this.privacyScanner = privacyScanner;
    }

    public PortfolioExportService(
            EvidenceClaimRepository claimRepository,
            JdRunRepository jdRunRepository,
            PortfolioExportRepository exportRepository
    ) {
        this(claimRepository, jdRunRepository, exportRepository, new PortfolioPrivacyScanner());
    }

    public PortfolioExportResponse export(PortfolioExportRequest request) {
        PortfolioExportRequest safeRequest = request == null ? new PortfolioExportRequest(null, null, null, null) : request;
        String format = safeRequest.safeFormat();
        if (!List.of("MARKDOWN", "HTML").contains(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前只支持 MARKDOWN 和 HTML 导出；PDF 需要后续接入渲染器。");
        }

        List<EvidenceClaim> claims = claimRepository.list(null, null, null, null, safeRequest.safeMaxClaims()).stream()
                .filter(this::exportableClaim)
                .toList();
        JdRunView jdRun = safeRequest.jdRunId() == null ? null : jdRunRepository.find(safeRequest.jdRunId());
        validateSavedEvidence(jdRun);
        String markdown = toMarkdown(safeRequest.safeTitle(), claims, jdRun);
        PrivacyScanResult privacy = privacyScanner.scan(markdown);
        String safeMarkdown = privacy.passed() ? markdown : privacyScanner.redact(markdown);
        String safeTitle = privacyScanner.redact(safeRequest.safeTitle());
        String content = "HTML".equals(format) ? toHtml(safeMarkdown, safeTitle) : safeMarkdown;
        Map<String, Object> metadata = metadata(claims, jdRun, privacy);
        PortfolioExportResponse response = new PortfolioExportResponse(
                UUID.randomUUID(),
                format,
                safeTitle,
                content,
                metadata,
                OffsetDateTime.now()
        );
        exportRepository.save(response);
        return response;
    }

    public PortfolioExportResponse find(UUID exportId) {
        return exportRepository.find(exportId);
    }

    public List<PortfolioExportResponse> list(Integer limit) {
        return exportRepository.list(limit == null || limit <= 0 ? 20 : limit);
    }

    private void validateSavedEvidence(JdRunView jdRun) {
        if (jdRun == null) return;
        Set<UUID> references = new LinkedHashSet<>();
        for (EvidenceMatrixRow row : jdRun.evidenceMatrix()) {
            addEvidenceReferences(references, row.evidenceOrEmpty());
        }
        ResumeDraft draft = jdRun.resumeDraft();
        if (draft != null && draft.interviewDefense() != null) {
            for (InterviewDefense card : draft.interviewDefense()) {
                addEvidenceReferences(references, card.evidence());
            }
        }
        if (!references.isEmpty() && claimRepository.hasBlockedEvidenceReferences(List.copyOf(references))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "保存的 JD 匹配或简历草稿包含未通过审核的证据，请重新匹配并生成草稿后再导出。");
        }
    }

    private void addEvidenceReferences(Set<UUID> references, List<MatchedEvidence> evidence) {
        if (evidence == null) return;
        for (MatchedEvidence item : evidence) {
            if (item.chunkId() != null) references.add(item.chunkId());
        }
    }

    private String toMarkdown(String title, List<EvidenceClaim> claims, JdRunView jdRun) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(title).append("\n\n");
        markdown.append("> 这个 Evidence Pack 只展示 TraceFolio 中已有公开证据。没有 quote 或 source 的内容不会进入正文；缺口和拒写项会单独标注。\n\n");

        appendExecutiveBrief(markdown, claims, jdRun);
        appendCapabilityRadar(markdown, claims);
        appendEvidencePages(markdown, claims);
        appendJdPack(markdown, jdRun);
        appendResumePack(markdown, jdRun);
        appendGapRoadmap(markdown, jdRun);
        appendTraceabilityAppendix(markdown, claims);
        appendUsageBoundaries(markdown);
        return markdown.toString();
    }

    private void appendExecutiveBrief(StringBuilder markdown, List<EvidenceClaim> claims, JdRunView jdRun) {
        Map<String, Long> strengthCounts = strengthCounts(claims);
        markdown.append("## 一页结论\n\n");
        markdown.append("- 产品定位：个人技术资产证据操作系统，不是普通聊天知识库或 ATS 关键词工具。\n");
        markdown.append("- 当前证据：").append(claims.size()).append(" 条 EvidenceClaim，其中 STRONG ")
                .append(strengthCounts.getOrDefault("STRONG", 0L)).append(" 条，MEDIUM ")
                .append(strengthCounts.getOrDefault("MEDIUM", 0L)).append(" 条，WEAK ")
                .append(strengthCounts.getOrDefault("WEAK", 0L)).append(" 条。\n");
        markdown.append("- 可分享内容：能力证据、项目证据、JD 匹配矩阵、简历草稿、面试防守、缺口补证据项目。\n");
        if (jdRun == null) {
            markdown.append("- JD 状态：本次导出未绑定 JD Run，只生成通用 Portfolio Evidence Pack。\n\n");
        } else {
            markdown.append("- JD 状态：绑定 JD Run `").append(jdRun.runId()).append("`，包含 ")
                    .append(jdRun.requirements().size()).append(" 条原子要求和 ")
                    .append(jdRun.evidenceMatrix().size()).append(" 条矩阵行。\n\n");
        }
    }

    private void appendCapabilityRadar(StringBuilder markdown, List<EvidenceClaim> claims) {
        markdown.append("## 能力雷达\n\n");
        if (claims.isEmpty()) {
            markdown.append("当前还没有 EvidenceClaim。请先运行 `POST /api/claims/extract`。\n\n");
            return;
        }

        for (CapabilitySummary summary : capabilitySummaries(claims).stream().limit(12).toList()) {
            markdown.append("### ").append(summary.capability()).append("\n\n");
            markdown.append("- 证据强度分：").append(String.format("%.1f", summary.score())).append("/100；证据数：")
                    .append(summary.total()).append("；强/中/弱：")
                    .append(summary.strong()).append("/")
                    .append(summary.medium()).append("/")
                    .append(summary.weak()).append("\n");
            markdown.append("- 技术栈：").append(joinOr(summary.technologies(), "未标注")).append("\n");
            markdown.append("- 涉及项目：").append(joinOr(summary.projects(), "未标注")).append("\n\n");
        }
    }

    private void appendEvidencePages(StringBuilder markdown, List<EvidenceClaim> claims) {
        markdown.append("## 项目证据页\n\n");
        if (claims.isEmpty()) {
            markdown.append("当前没有可展示项目证据。\n\n");
            return;
        }
        Map<String, List<EvidenceClaim>> byProject = claims.stream()
                .collect(Collectors.groupingBy(
                        claim -> firstNonBlank(claim.projectName(), claim.title()),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        byProject.entrySet().stream().limit(10).forEach(entry -> {
            markdown.append("### ").append(nullToBlank(entry.getKey())).append("\n\n");
            for (EvidenceClaim claim : entry.getValue().stream()
                    .sorted(Comparator.comparing(EvidenceClaim::confidence).reversed())
                    .limit(5)
                    .toList()) {
                markdown.append("- ").append(nullToBlank(claim.capability()));
                if (!nullToBlank(claim.technology()).isBlank()) {
                    markdown.append(" / ").append(claim.technology());
                }
                markdown.append("：").append(nullToBlank(claim.action())).append("。");
                markdown.append(" 强度 ").append(nullToBlank(claim.strength()))
                        .append("，风险 ").append(nullToBlank(claim.riskLevel())).append("。\n");
                markdown.append("  - 来源：").append(nullToBlank(claim.sourceUri())).append("\n");
                markdown.append("  - Quote：").append(oneLine(claim.quote(), 180)).append("\n");
            }
            markdown.append("\n");
        });
    }

    private void appendJdPack(StringBuilder markdown, JdRunView jdRun) {
        if (jdRun == null) {
            return;
        }
        markdown.append("## JD 匹配包\n\n");
        Map<String, Long> supportCounts = supportCounts(jdRun.evidenceMatrix());
        markdown.append("- SUPPORTED：").append(supportCounts.getOrDefault("SUPPORTED", 0L)).append("\n");
        markdown.append("- WEAKLY_SUPPORTED：").append(supportCounts.getOrDefault("WEAKLY_SUPPORTED", 0L)).append("\n");
        markdown.append("- GAP_PROJECT_REQUIRED：").append(supportCounts.getOrDefault("GAP_PROJECT_REQUIRED", 0L)).append("\n");
        markdown.append("- UNSUPPORTED：").append(supportCounts.getOrDefault("UNSUPPORTED", 0L)).append("\n\n");

        for (EvidenceMatrixRow row : jdRun.evidenceMatrix().stream().limit(20).toList()) {
            markdown.append("### ").append(row.requirementId()).append(" · ").append(row.supportStatus()).append("\n\n");
            markdown.append("- 要求：").append(nullToBlank(row.requirement())).append("\n");
            markdown.append("- 可写法/缺口：").append(firstNonBlank(row.allowedResumeWording(), row.missingGap())).append("\n");
            if (!row.evidenceOrEmpty().isEmpty()) {
                markdown.append("- 证据：\n");
                row.evidenceOrEmpty().stream().limit(2).forEach(evidence -> {
                    markdown.append("  - ").append(oneLine(evidence.quote(), 180)).append("\n");
                    markdown.append("    来源：").append(nullToBlank(evidence.sourceUri())).append("\n");
                });
            }
            markdown.append("\n");
        }
    }

    private void appendResumePack(StringBuilder markdown, JdRunView jdRun) {
        if (jdRun == null || jdRun.resumeDraft() == null) {
            return;
        }
        ResumeDraft draft = jdRun.resumeDraft();
        markdown.append("## 岗位定制简历草稿\n\n");
        appendList(markdown, "技能摘要", draft.skillSummary());
        markdown.append("### 项目经历\n\n");
        if (draft.projectExperiences() == null || draft.projectExperiences().isEmpty()) {
            markdown.append("没有足够证据生成项目经历 bullet。\n\n");
        } else {
            for (ProjectExperience project : draft.projectExperiences()) {
                markdown.append("#### ").append(nullToBlank(project.projectName())).append("\n\n");
                markdown.append("- 来源：").append(nullToBlank(project.sourceUri())).append("\n");
                for (String bullet : safeList(project.bullets())) {
                    markdown.append("- ").append(bullet).append("\n");
                }
                markdown.append("\n");
            }
        }
        appendList(markdown, "自我评价", draft.selfEvaluation());
        if (!nullToBlank(draft.workExperienceNotice()).isBlank()) {
            markdown.append("### 工作经历边界\n\n");
            markdown.append(draft.workExperienceNotice()).append("\n\n");
        }
        appendInterviewDefense(markdown, draft.interviewDefense());
    }

    private void appendInterviewDefense(StringBuilder markdown, List<InterviewDefense> defenses) {
        markdown.append("## 面试防守卡\n\n");
        if (defenses == null || defenses.isEmpty()) {
            markdown.append("当前没有面试防守卡。请先绑定 JD Run 并运行 resume compile。\n\n");
            return;
        }
        for (InterviewDefense defense : defenses.stream().limit(12).toList()) {
            markdown.append("### ").append(nullToBlank(defense.possibleQuestion())).append("\n\n");
            markdown.append("- 可防守回答：").append(nullToBlank(defense.defensibleAnswer())).append("\n");
            markdown.append("- 边界：").append(nullToBlank(defense.boundary())).append("\n");
            if (defense.evidence() != null && !defense.evidence().isEmpty()) {
                markdown.append("- 证据：").append(oneLine(defense.evidence().getFirst().quote(), 180)).append("\n");
            }
            markdown.append("\n");
        }
    }

    private void appendGapRoadmap(StringBuilder markdown, JdRunView jdRun) {
        markdown.append("## 缺口补证据路线图\n\n");
        if (jdRun == null || jdRun.gapProjects() == null || jdRun.gapProjects().isEmpty()) {
            markdown.append("当前没有缺口项目计划。没有缺口并不等于能力完备，只表示本次 JD 矩阵没有生成 GAP_PROJECT_REQUIRED 计划。\n\n");
            return;
        }
        jdRun.gapProjects().stream().limit(8).forEach(project -> {
            markdown.append("### ").append(nullToBlank(project.projectName())).append("\n\n");
            markdown.append("- 对应能力：").append(nullToBlank(project.capability())).append("\n");
            markdown.append("- 目标：").append(nullToBlank(project.objective())).append("\n");
            markdown.append("- GitHub 搜索策略：`").append(nullToBlank(project.searchQuery())).append("`\n");
            appendList(markdown, "功能范围", project.scope());
            appendList(markdown, "验收标准", project.acceptanceCriteria());
            appendList(markdown, "README 应产出证据", project.readmeEvidenceToProduce());
            markdown.append("- 完成后允许写法：").append(nullToBlank(project.allowedResumeWordingAfterDone())).append("\n");
            markdown.append("- 仍然不能写：").append(nullToBlank(project.stillCannotClaim())).append("\n\n");
        });
    }

    private void appendTraceabilityAppendix(StringBuilder markdown, List<EvidenceClaim> claims) {
        markdown.append("## Traceability Appendix\n\n");
        if (claims.isEmpty()) {
            markdown.append("暂无 traceable claim。\n\n");
            return;
        }
        markdown.append("## 可引用证据\n\n");
        for (EvidenceClaim claim : claims) {
            markdown.append("### ").append(nullToBlank(claim.capability())).append(" · ")
                    .append(nullToBlank(claim.technology())).append("\n\n");
            markdown.append("- 项目/资料：").append(nullToBlank(claim.projectName())).append("\n");
            markdown.append("- 强度：").append(nullToBlank(claim.strength())).append("；风险：")
                    .append(nullToBlank(claim.riskLevel())).append("\n");
            markdown.append("- 来源：").append(nullToBlank(claim.sourceUri())).append("\n\n");
            markdown.append("> ").append(nullToBlank(claim.quote()).replace("\n", "\n> ")).append("\n\n");
        }
    }

    private void appendUsageBoundaries(StringBuilder markdown) {
        markdown.append("## 使用边界\n\n");
        markdown.append("- 可以写：被上方 quote 支撑的个人项目、开源项目、技术复盘和学习实践。\n");
        markdown.append("- 弱证据只能弱表达：不能升级成生产级、主导、大规模采用或商业结果。\n");
        markdown.append("- 不可以写：没有公开证据的大厂经历、正式工作经历、千万级系统、收入、企业采用。\n");
        markdown.append("\n");
    }

    private String toHtml(String markdown, String title) {
        String body = htmlRenderer.render(markdownParser.parse(markdown));
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    :root{color-scheme:light;--ink:#17202a;--muted:#637083;--line:#d8e1ea;--brand:#0b6b73;--soft:#f5f8fb}
                    body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif;margin:0;color:var(--ink);line-height:1.72;background:#eef3f7}
                    header{background:#10242b;color:#fff;padding:40px 24px}
                    header div{max-width:1040px;margin:0 auto}
                    header p{color:#c7d8df;max-width:760px}
                    main{max-width:1040px;margin:0 auto;padding:28px 24px 56px;background:#fff}
                    h1,h2,h3,h4{line-height:1.25;color:#14212b;letter-spacing:0}
                    header h1{color:#fff;margin:0 0 10px;font-size:34px}
                    h2{border-top:1px solid var(--line);padding-top:24px;margin-top:32px}
                    code{background:#eef3f7;padding:2px 5px;border-radius:4px}
                    li{margin:5px 0}
                    blockquote{border-left:4px solid var(--brand);margin:16px 0;padding:8px 12px;background:#f8fafc}
                    a{color:#064f57;word-break:break-word}
                    @media (max-width:720px){header{padding:28px 18px}main{padding:20px 18px 42px}header h1{font-size:28px}}
                  </style>
                </head>
                <body>
                  <header><div><h1>%s</h1><p>可分享的个人技术资产证据包。每个能力表达都应回到公开来源、quote 和证据边界。</p></div></header>
                  <main>%s</main>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(title), body);
    }

    private Map<String, Object> metadata(List<EvidenceClaim> claims, JdRunView jdRun, PrivacyScanResult privacy) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Long> strengthCounts = strengthCounts(claims);
        metadata.put("claimCount", claims.size());
        metadata.put("strongClaims", strengthCounts.getOrDefault("STRONG", 0L));
        metadata.put("mediumClaims", strengthCounts.getOrDefault("MEDIUM", 0L));
        metadata.put("weakClaims", strengthCounts.getOrDefault("WEAK", 0L));
        metadata.put("capabilityCount", capabilitySummaries(claims).size());
        metadata.put("evidenceScore", evidenceScore(claims));
        metadata.put("jdRunId", jdRun == null ? null : jdRun.runId());
        metadata.put("matrixRows", jdRun == null ? 0 : jdRun.evidenceMatrix().size());
        metadata.put("supportCounts", jdRun == null ? Map.of() : supportCounts(jdRun.evidenceMatrix()));
        metadata.put("resumeProjectCount", jdRun == null || jdRun.resumeDraft() == null || jdRun.resumeDraft().projectExperiences() == null
                ? 0
                : jdRun.resumeDraft().projectExperiences().size());
        metadata.put("defenseCardCount", jdRun == null || jdRun.resumeDraft() == null || jdRun.resumeDraft().interviewDefense() == null
                ? 0
                : jdRun.resumeDraft().interviewDefense().size());
        metadata.put("gapProjectCount", jdRun == null || jdRun.gapProjects() == null ? 0 : jdRun.gapProjects().size());
        metadata.put("privacyStatus", privacy.status());
        metadata.put("privacyIssues", privacy.issues());
        return metadata;
    }

    private boolean exportableClaim(EvidenceClaim claim) {
        return claim != null
                && "VALID".equals(claim.validationStatus())
                && !nullToBlank(claim.quote()).isBlank()
                && !nullToBlank(claim.sourceUri()).isBlank();
    }

    private void appendList(StringBuilder markdown, String title, List<String> items) {
        markdown.append("### ").append(title).append("\n\n");
        List<String> safeItems = safeList(items);
        if (safeItems.isEmpty()) {
            markdown.append("暂无。\n\n");
            return;
        }
        for (String item : safeItems) {
            markdown.append("- ").append(item).append("\n");
        }
        markdown.append("\n");
    }

    private List<CapabilitySummary> capabilitySummaries(List<EvidenceClaim> claims) {
        Map<String, List<EvidenceClaim>> grouped = claims.stream()
                .collect(Collectors.groupingBy(
                        claim -> firstNonBlank(claim.capability(), "未标注能力"),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        List<CapabilitySummary> summaries = new ArrayList<>();
        for (Map.Entry<String, List<EvidenceClaim>> entry : grouped.entrySet()) {
            List<EvidenceClaim> items = entry.getValue();
            long strong = countStrength(items, "STRONG");
            long medium = countStrength(items, "MEDIUM");
            long weak = countStrength(items, "WEAK");
            List<String> technologies = distinctNonBlank(items.stream().map(EvidenceClaim::technology).toList());
            List<String> projects = distinctNonBlank(items.stream().map(EvidenceClaim::projectName).toList());
            double score = Math.min(100, ((strong * 3.0 + medium * 2.0 + weak) / Math.max(1, items.size() * 3.0)) * 100);
            summaries.add(new CapabilitySummary(entry.getKey(), items.size(), strong, medium, weak, technologies, projects, score));
        }
        return summaries.stream()
                .sorted(Comparator.comparing(CapabilitySummary::score).reversed()
                        .thenComparing(CapabilitySummary::total, Comparator.reverseOrder()))
                .toList();
    }

    private Map<String, Long> strengthCounts(List<EvidenceClaim> claims) {
        return claims.stream().collect(Collectors.groupingBy(
                claim -> firstNonBlank(claim.strength(), "UNKNOWN"),
                LinkedHashMap::new,
                Collectors.counting()
        ));
    }

    private Map<String, Long> supportCounts(List<EvidenceMatrixRow> matrix) {
        return matrix.stream().collect(Collectors.groupingBy(
                row -> firstNonBlank(row.supportStatus(), "UNKNOWN"),
                LinkedHashMap::new,
                Collectors.counting()
        ));
    }

    private long countStrength(List<EvidenceClaim> claims, String strength) {
        return claims.stream().filter(claim -> strength.equals(claim.strength())).count();
    }

    private int evidenceScore(List<EvidenceClaim> claims) {
        if (claims.isEmpty()) {
            return 0;
        }
        double weighted = claims.stream()
                .mapToDouble(claim -> switch (firstNonBlank(claim.strength(), "WEAK")) {
                    case "STRONG" -> 3.0;
                    case "MEDIUM" -> 2.0;
                    default -> 1.0;
                })
                .sum();
        return (int) Math.round(Math.min(100, weighted / (claims.size() * 3.0) * 100));
    }

    private List<String> distinctNonBlank(List<String> values) {
        return values.stream()
                .map(this::nullToBlank)
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(8)
                .toList();
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private String joinOr(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? fallback : String.join("、", values);
    }

    private String oneLine(String value, int limit) {
        String text = nullToBlank(value).replaceAll("\\s+", " ").strip();
        return text.length() <= limit ? text : text.substring(0, Math.max(0, limit - 1)) + "...";
    }

    private String escapeHtml(String value) {
        return nullToBlank(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? nullToBlank(second) : first;
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private record CapabilitySummary(
            String capability,
            long total,
            long strong,
            long medium,
            long weak,
            List<String> technologies,
            List<String> projects,
            double score
    ) {
    }
}
