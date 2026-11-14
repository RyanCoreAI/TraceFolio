package com.yanxai.tracefolio.roadmap;

import com.yanxai.tracefolio.audit.EvidenceOsAuditResult;
import com.yanxai.tracefolio.audit.EvidenceOsAuditService;
import com.yanxai.tracefolio.document.DocumentQueryService;
import com.yanxai.tracefolio.document.KnowledgeStats;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProductRoadmapService {

    private final DocumentQueryService documentQueryService;
    private final EvidenceOsAuditService auditService;

    public ProductRoadmapService(DocumentQueryService documentQueryService, EvidenceOsAuditService auditService) {
        this.documentQueryService = documentQueryService;
        this.auditService = auditService;
    }

    public ProductRoadmapResponse roadmap() {
        RuntimeEvidence evidence = runtimeEvidence();
        List<RoadmapPhase> phases = phases(evidence);
        int overallCompletion = (int) Math.round(phases.stream()
                .mapToInt(RoadmapPhase::completion)
                .average()
                .orElse(0));

        return new ProductRoadmapResponse(
                "TraceFolio",
                "个人技术资产证据操作系统：把公开资料和代码仓库编译成可引用、可回放、可拒写的能力证据。",
                stage(overallCompletion, evidence),
                overallCompletion,
                evidence.asMap(),
                differentiators(),
                phases,
                releaseGate(),
                blockers(evidence),
                OffsetDateTime.now()
        );
    }

    private RuntimeEvidence runtimeEvidence() {
        KnowledgeStats stats;
        String statsError = null;
        try {
            stats = documentQueryService.stats();
        } catch (Exception e) {
            stats = new KnowledgeStats(0, 0, 0, 0, 0, 0, 0, 0);
            statsError = e.getMessage();
        }

        EvidenceOsAuditResult audit = null;
        String auditError = null;
        try {
            audit = auditService.audit();
        } catch (Exception e) {
            auditError = e.getMessage();
        }
        return new RuntimeEvidence(stats, statsError, audit, auditError);
    }

    private List<RoadmapPhase> phases(RuntimeEvidence evidence) {
        return List.of(
                new RoadmapPhase(
                        "V1",
                        "可信 RAG Core",
                        status(evidence.stats().chunks() > 0 && evidence.stats().evalRuns() > 0),
                        evidence.stats().chunks() > 0 ? evidence.stats().evalRuns() > 0 ? 92 : 84 : 78,
                        "稳定导入公开资料，Hybrid Retrieval 后只回答能被 citation 支撑的问题。",
                        List.of(
                                "GitHub README/docs 和博客导入",
                                "PGVector + PostgreSQL FTS + RRF 检索",
                                "DeepSeek JSON 输出、citation validator、abstention",
                                "Trace replay 和 eval runner"
                        ),
                        List.of(
                                "citation.quote 必须出现在本轮 chunk 原文中",
                                "无证据问题必须 ABSTAINED",
                                "每次改 chunking/retrieval/prompt 后跑 eval"
                        ),
                        evidence.stats().evalRuns() > 0 ? List.of() : List.of("当前数据库还没有 eval run 运行记录。"),
                        List.of("POST /api/ingest/github", "POST /api/qa", "GET /api/traces/{traceId}", "POST /api/evals/run")
                ),
                new RoadmapPhase(
                        "V2",
                        "EvidenceClaim Layer",
                        status(evidence.stats().claims() > 0),
                        evidence.stats().claims() > 0 ? 90 : 82,
                        "把 chunk 和 repo scan 抽成可复用能力证据节点，作为简历、面试和 Portfolio 的统一底座。",
                        List.of(
                                "EvidenceClaim schema",
                                "claim strength、riskLevel、quote、source 回链",
                                "chunk claim extractor",
                                "claim 查询和审计"
                        ),
                        List.of(
                                "无 quote 的 claim 不能进入简历",
                                "每个 claim 必须回链到 chunk 或 repo scan",
                                "弱证据只能生成弱表达"
                        ),
                        evidence.auditReady() ? List.of() : List.of("Evidence OS audit 尚未达到 READY，需要补真实资料、claim、repo scan、workflow 或 portfolio export。"),
                        List.of("POST /api/claims/extract", "GET /api/claims", "GET /api/audit/evidence-os")
                ),
                new RoadmapPhase(
                        "V3",
                        "JD Evidence Compiler",
                        "IMPLEMENTED",
                        88,
                        "把 JD 编译成原子要求，再映射到真实 EvidenceClaim，输出可写、弱写、拒写和补项目判断。",
                        List.of(
                                "JD Requirement Parser",
                                "JD Evidence Matrix",
                                "Resume Claim Compiler",
                                "Gap-to-Project Planner",
                                "Interview defense 输出"
                        ),
                        List.of(
                                "真实 JD 至少拆出 10 条原子要求",
                                "进入简历的 bullet 必须有 quote",
                                "大厂经历、千万级系统、商业结果等无证据 claim 必须拒写"
                        ),
                        List.of("需要继续扩大真实 JD 样本和 golden eval。"),
                        List.of("POST /api/jd/compile", "POST /api/jd/match", "POST /api/resume/compile", "POST /api/gaps/projects")
                ),
                new RoadmapPhase(
                        "V4",
                        "Repo Evidence Extractor",
                        status(evidence.stats().repoScans() > 0),
                        evidence.stats().repoScans() > 0 ? 88 : 84,
                        "只读扫描 GitHub 或用户确认拥有的本地仓库，把代码结构、配置、测试、部署文件转换为能力证据。",
                        List.of(
                                "GitHub 仓库扫描",
                                "本地仓库只读扫描和 ownership confirmation",
                                "README、docs、Docker、CI、migration、Controller/Service/test 信号抽取",
                                "repo evidence 自动写入 EvidenceClaim"
                        ),
                        List.of(
                                "本地扫描必须 confirmOwnership=true",
                                "local source URI 不泄露本机绝对路径",
                                "扫描结果必须沉淀为 repo claim"
                        ),
                        evidence.stats().repoScans() > 0 ? List.of() : List.of("当前数据库还没有 repo scan 运行记录。"),
                        List.of("POST /api/repos/scan")
                ),
                new RoadmapPhase(
                        "V5",
                        "Auditable Agent Workflow",
                        status(evidence.stats().workflows() > 0),
                        evidence.stats().workflows() > 0 ? 87 : 83,
                        "用可审计编排串起 claim extract、JD compile、match、resume、gap、repo scan，每步可回放、可失败、可人工决策。",
                        List.of(
                                "JD Evidence Workflow",
                                "Repo Evidence Workflow",
                                "workflow step input/output/error 落库",
                                "human review decision 记录"
                        ),
                        List.of(
                                "每个 workflow 必须有 step",
                                "高风险输出必须可人工复核",
                                "失败必须定位到具体 step"
                        ),
                        evidence.stats().workflows() > 0 ? List.of() : List.of("当前数据库还没有 workflow run 运行记录。"),
                        List.of("POST /api/workflows/jd-evidence/run", "POST /api/workflows/repo-evidence/run", "GET /api/workflows/{runId}")
                ),
                new RoadmapPhase(
                        "V6",
                        "Portfolio Evidence Pack",
                        status(evidence.stats().portfolioExports() > 0),
                        evidence.stats().portfolioExports() > 0 ? 86 : 80,
                        "生成可分享的能力证据包，展示能力雷达、项目证据、JD 匹配、面试防守、缺口路线和拒写边界。",
                        List.of(
                                "Markdown / HTML Evidence Pack 导出",
                                "能力雷达和证据强弱统计",
                                "JD matrix、resume draft、interview defense、gap projects 聚合",
                                "使用边界和 traceability appendix"
                        ),
                        List.of(
                                "导出内容不能为空",
                                "展示项必须有 source 或明确标注为缺口/拒写",
                                "HTML/Markdown 均可直接分享或放入 README"
                        ),
                        evidence.stats().portfolioExports() > 0 ? List.of() : List.of("当前数据库还没有 portfolio export 运行记录。"),
                        List.of("POST /api/portfolio/export", "GET /api/product/roadmap")
                )
        );
    }

    private String status(boolean hasRuntimeEvidence) {
        return hasRuntimeEvidence ? "RUNTIME_VERIFIED" : "CODE_COMPLETE_NEEDS_RUNTIME_DATA";
    }

    private String stage(int completion, RuntimeEvidence evidence) {
        if (completion >= 85 && evidence.auditReady()) {
            return "PRODUCT_BASELINE_READY";
        }
        if (completion >= 85) {
            return "FULL_ROADMAP_CODE_COMPLETE";
        }
        return "IMPLEMENTATION_BASELINE";
    }

    private List<String> differentiators() {
        return List.of(
                "不是普通知识库：每个回答都必须能回到 source、chunk、quote、trace。",
                "不是 ATS 关键词工具：简历每句话都经过 SUPPORTED / WEAKLY_SUPPORTED / UNSUPPORTED / GAP_PROJECT_REQUIRED 判断。",
                "不是通用代码助手：仓库扫描只抽取可证明能力的证据，不自动改用户仓库。",
                "不是黑盒 Agent：工作流每一步都有输入、输出、失败原因和人工决策记录。",
                "缺证据不硬写：直接生成 3-7 天最小补证据项目和完成后的允许写法。"
        );
    }

    private List<String> releaseGate() {
        return List.of(
                "启动 PostgreSQL + PGVector + Ollama + Spring Boot，确保 /api/runtime/status ready=true 或明确展示缺项。",
                "导入至少 1 篇博客、1 个 README、1 个仓库扫描结果。",
                "运行 claim extract、JD workflow、repo workflow、portfolio export。",
                "运行 eval，拒写类问题不得强答，ANSWERED 必须有 citation。",
                "前端桌面和移动端可完成导入、扫描、JD、workflow、QA、trace、export 主链路。"
        );
    }

    private List<String> blockers(RuntimeEvidence evidence) {
        if (evidence.statsError() != null || evidence.auditError() != null) {
            return List.of("运行时数据库或 audit 查询不可用：" + firstNonBlank(evidence.statsError(), evidence.auditError()));
        }
        return evidence.auditReady()
                ? List.of()
                : List.of("代码基线已具备，仍需要真实 Docker/数据库环境跑端到端 smoke，让 audit 从 PARTIAL/BLOCKED 进入 READY。");
    }

    private String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }

    private record RuntimeEvidence(
            KnowledgeStats stats,
            String statsError,
            EvidenceOsAuditResult audit,
            String auditError
    ) {
        boolean auditReady() {
            return audit != null && "READY".equals(audit.status());
        }

        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("documents", stats.documents());
            map.put("chunks", stats.chunks());
            map.put("traces", stats.traces());
            map.put("evalRuns", stats.evalRuns());
            map.put("claims", stats.claims());
            map.put("repoScans", stats.repoScans());
            map.put("workflows", stats.workflows());
            map.put("portfolioExports", stats.portfolioExports());
            map.put("auditStatus", audit == null ? "UNAVAILABLE" : audit.status());
            map.put("auditPassedChecks", audit == null ? 0 : audit.passedChecks());
            map.put("auditTotalChecks", audit == null ? 0 : audit.totalChecks());
            map.put("statsError", statsError);
            map.put("auditError", auditError);
            return map;
        }
    }
}
