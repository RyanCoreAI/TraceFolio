package com.yanxai.tracefolio.eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.qa.QaRequest;
import com.yanxai.tracefolio.qa.QaResponse;
import com.yanxai.tracefolio.qa.QaService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EvalRunner {

    private final TraceFolioProperties properties;
    private final QaService qaService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public EvalRunner(
            TraceFolioProperties properties,
            QaService qaService,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.qaService = qaService;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public EvalRunResult run() {
        return run(new EvalRunRequest("core", List.of(), false));
    }

    public EvalRunResult run(EvalRunRequest request) {
        EvalRunRequest safeRequest = request == null ? new EvalRunRequest("core", List.of(), false) : request;
        List<EvalCase> cases = casesFor(safeRequest);
        UUID runId = UUID.randomUUID();
        int passed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        createRun(runId, safeRequest, cases.size());
        for (EvalCase evalCase : cases) {
            QaResponse response = null;
            String failure;
            try {
                response = qaService.answer(new QaRequest(evalCase.question()));
                failure = evaluate(evalCase, response);
            } catch (Exception e) {
                failure = "case failed with exception: " + e.getMessage();
            }
            boolean ok = failure == null;
            if (ok) {
                passed++;
            } else {
                failed++;
                failures.add(evalCase.question() + " -> " + failure);
            }
            saveCaseResult(runId, evalCase.id(), response == null ? null : response.traceId(), ok, failure);
            if (!ok && safeRequest.shouldFailFast()) {
                break;
            }
        }

        updateRun(runId, passed, failed, failures);
        int total = passed + failed;
        return new EvalRunResult(
                runId,
                safeRequest.safeSuite(),
                total,
                passed,
                failed,
                total == 0 ? 0.0 : Math.round((passed * 10000.0 / total)) / 100.0,
                safeRequest.tagsOrEmpty(),
                failures
        );
    }

    private String evaluate(EvalCase evalCase, QaResponse response) {
        if (!evalCase.expectedStatus().equals(response.status())) {
            return "expected status " + evalCase.expectedStatus() + " but got " + response.status();
        }
        if ("ANSWERED".equals(response.status()) && response.citations().isEmpty()) {
            return "ANSWERED response has no citation";
        }
        String answer = response.answer() == null ? "" : response.answer();
        for (String term : evalCase.requiredTerms()) {
            if (!answer.contains(term)) {
                return "missing required term: " + term;
            }
        }
        for (String term : evalCase.forbiddenTerms()) {
            if (answer.contains(term)) {
                return "contains forbidden term: " + term;
            }
        }
        return null;
    }

    private void createRun(UUID runId, EvalRunRequest request, int total) {
        jdbcTemplate.update("""
                INSERT INTO rag_eval_run (id, tenant_id, suite, tags, total_cases, passed_cases, failed_cases, summary)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?::jsonb)
                """,
                runId,
                properties.tenantId(),
                request.safeSuite(),
                toJson(request.tagsOrEmpty()),
                total,
                0,
                0,
                toJson(List.of())
        );
    }

    private void updateRun(UUID runId, int passed, int failed, List<String> failures) {
        jdbcTemplate.update("""
                UPDATE rag_eval_run
                SET passed_cases = ?, failed_cases = ?, summary = ?::jsonb
                WHERE id = ?
                """,
                passed,
                failed,
                toJson(failures),
                runId
        );
    }

    private void saveCaseResult(UUID runId, UUID caseId, UUID traceId, boolean passed, String failureReason) {
        jdbcTemplate.update("""
                INSERT INTO rag_eval_case_result (id, run_id, case_id, trace_id, passed, failure_reason)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                runId,
                caseId,
                traceId,
                passed,
                failureReason
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize eval json", e);
        }
    }

    private List<EvalCase> casesFor(EvalRunRequest request) {
        List<EvalCase> all = defaultCases();
        String suite = request.safeSuite();
        if ("all".equals(suite)) {
            return filterTags(all, request.tagsOrEmpty());
        }
        List<EvalCase> selected = all.stream()
                .filter(evalCase -> suite.equalsIgnoreCase(evalCase.category())
                        || evalCase.category().toLowerCase().startsWith(suite + "_")
                        || ("core".equals(suite) && List.of("SHOULD_ANSWER", "MUST_CITE", "MUST_ABSTAIN").contains(evalCase.category())))
                .toList();
        return filterTags(selected, request.tagsOrEmpty());
    }

    private List<EvalCase> filterTags(List<EvalCase> cases, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return cases;
        }
        return cases.stream()
                .filter(evalCase -> tags.stream().anyMatch(tag -> evalCase.question().toLowerCase().contains(tag)))
                .toList();
    }

    private List<EvalCase> defaultCases() {
        List<EvalCase> cases = new ArrayList<>();
        add(cases, "SHOULD_ANSWER", "PatchBrake 是什么？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "Token Studio ROI 解决什么问题？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "Ryan 的 RAG 专题核心观点是什么？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "OmniMerchant 体现了哪些 Spring AI 能力？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "Ryan 为什么关注可信 AI 应用工程？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "Ryan 做过哪些和 AI 编程安全有关的项目？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "哪些项目能体现 Ryan 对证据链的关注？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "Ryan 的公开资料里有哪些 Spring AI 实战内容？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "PatchBrake 和 AI-generated diff risk 有什么关系？", "ANSWERED");
        add(cases, "SHOULD_ANSWER", "Token Studio ROI 为什么不是普通笔记项目？", "ANSWERED");

        add(cases, "MUST_CITE", "哪篇文章提到 RAG 不是外挂知识库，而是证据链系统？", "ANSWERED");
        add(cases, "MUST_CITE", "PatchBrake 的 README 是否说明它关注 AI-generated diff risk？", "ANSWERED");
        add(cases, "MUST_CITE", "Token Studio ROI 的文章是否提到 AI 编程 ROI 复盘？", "ANSWERED");
        add(cases, "MUST_CITE", "哪个项目文档能支撑 Ryan 关注 AI observability？", "ANSWERED");
        add(cases, "MUST_CITE", "哪份 README 能支撑 Ryan 做过 Spring AI 项目？", "ANSWERED");
        add(cases, "MUST_CITE", "简喵和普通 AI 简历工具的差异有没有公开证据？", "ANSWERED");
        add(cases, "MUST_CITE", "OmniMerchant 的 Spring AI 能力来自哪份文档？", "ANSWERED");
        add(cases, "MUST_CITE", "Ryan 的可信 AI 应用工程说法能由哪些来源支撑？", "ANSWERED");
        add(cases, "MUST_CITE", "哪份资料提到 citation 或 trace？", "ANSWERED");
        add(cases, "MUST_CITE", "哪份资料能说明 TraceFolio 的设计原则？", "ANSWERED");

        add(cases, "MUST_ABSTAIN", "Ryan 是否有大厂实习经历？", "ABSTAINED", List.of(), List.of("有大厂实习"));
        add(cases, "MUST_ABSTAIN", "Ryan 是否主导过千万级生产系统？", "ABSTAINED", List.of(), List.of("主导过千万级"));
        add(cases, "MUST_ABSTAIN", "PatchBrake 是否已经被企业大规模采用？", "ABSTAINED", List.of(), List.of("大规模采用"));
        add(cases, "MUST_ABSTAIN", "Token Studio ROI 是否已经商业化盈利？", "ABSTAINED", List.of(), List.of("商业化盈利"));
        add(cases, "MUST_ABSTAIN", "OmniMerchant 是否已经上线真实跨境商家？", "ABSTAINED", List.of(), List.of("已经上线"));
        add(cases, "MUST_ABSTAIN", "Ryan 是否拿过千万融资？", "ABSTAINED", List.of(), List.of("千万融资"));
        add(cases, "MUST_ABSTAIN", "Ryan 是否在顶会发表过 RAG 论文？", "ABSTAINED", List.of(), List.of("顶会发表"));
        add(cases, "MUST_ABSTAIN", "Ryan 是否担任过大厂架构师？", "ABSTAINED", List.of(), List.of("大厂架构师"));
        add(cases, "MUST_ABSTAIN", "Ryan 的项目是否已经服务百万用户？", "ABSTAINED", List.of(), List.of("百万用户"));
        add(cases, "MUST_ABSTAIN", "Ryan 是否拥有某家公司的正式背书？", "ABSTAINED", List.of(), List.of("正式背书"));
        addJdCases(cases);
        addRepoCases(cases);
        addWorkflowCases(cases);
        addPortfolioCases(cases);
        return cases;
    }

    private void addJdCases(List<EvalCase> cases) {
        List<String> jdPrompts = List.of(
                "JD Evidence Compiler 是否会把 JD 拆成原子要求？",
                "TraceFolio 如何判断一条简历 claim 是 SUPPORTED？",
                "弱证据能力应该如何写进简历？",
                "无证据的大厂经历能否进入简历？",
                "缺少 Spring AI 项目证据时系统应该输出什么？",
                "JD 匹配矩阵是否包含 quote 和 source？",
                "面试防守包解决什么问题？",
                "岗位要求里的经验年限为什么是高风险要求？",
                "简历生成为什么不能伪造工作经历？",
                "Gap-to-Project Planner 的目标是什么？",
                "普通 ATS 关键词优化和 TraceFolio 有什么差异？",
                "JD parser 会识别哪些岗位要求类别？",
                "allowed resume wording 的边界是什么？",
                "GAP_PROJECT_REQUIRED 是否可以直接写入简历？",
                "Ryan 是否可以把参考 GitHub 项目写成自己的经历？",
                "TraceFolio 是否优先生成工作经历？",
                "JD Evidence Compiler 的核心输出是什么？",
                "哪些 claim status 会被简历编译器使用？",
                "无 quote 的 EvidenceClaim 能否进入 resume compile？",
                "为什么要保存 JD run 回放？"
        );
        addAnswered(cases, "jd", jdPrompts);
    }

    private void addRepoCases(List<EvalCase> cases) {
        List<String> repoPrompts = List.of(
                "Repo Evidence Extractor 是否会修改用户仓库？",
                "本地仓库扫描为什么需要 confirmOwnership=true？",
                "仓库扫描会抽取哪些工程证据？",
                "GitHub stars 能否证明项目质量？",
                "local sourceUri 是否应该暴露本机绝对路径？",
                "repo scan 如何把证据写入 EvidenceClaim？",
                "扫描 Docker 文件能证明什么能力？",
                "扫描 migration 文件能证明什么能力？",
                "扫描 Controller 和 Service 能证明什么能力？",
                "GitHub tree truncated 时应该如何处理？",
                "Repo scanner 为什么只读扫描？",
                "扫描 README 和 docs 能支撑哪些 claim？",
                "TraceFolio 是否会自动修改参考项目？",
                "仓库证据的 line anchor 有什么作用？",
                "Repo scanner 的 FAST 和 DEEP 模式有什么差异？",
                "private repo 内容是否可以默认导出？",
                "file hash 在 repo snapshot 中有什么作用？",
                "CI workflow 能证明哪些工程交付能力？",
                "测试目录能证明什么能力？",
                "参考项目 stars 是否能作为简历价值证明？"
        );
        addAnswered(cases, "repo", repoPrompts);
    }

    private void addWorkflowCases(List<EvalCase> cases) {
        List<String> workflowPrompts = List.of(
                "Auditable Workflow 保存哪些信息？",
                "workflow step 为什么需要 input 和 output？",
                "高风险 resume compile 为什么需要人工确认？",
                "WAITING_REVIEW 状态代表什么？",
                "workflow retry 应该重放哪一步？",
                "Product Loop Workflow 串起了哪些阶段？",
                "human decision 会保存哪些字段？",
                "FAILED_VALIDATION 和 FAILED 有什么差异？",
                "workflow replay 对可信 RAG 有什么价值？",
                "local repo scan 为什么是高风险步骤？",
                "portfolio export 为什么要进入 workflow？",
                "workflow resume 为什么不能自动越过人工确认？",
                "每个 workflow 是否必须有 step？",
                "TraceFolio 是否是黑盒 Agent？",
                "Agent 工作流为什么不自动投递简历？"
        );
        addAnswered(cases, "workflow", workflowPrompts);
    }

    private void addPortfolioCases(List<EvalCase> cases) {
        List<String> portfolioPrompts = List.of(
                "Portfolio Evidence Pack 包含哪些内容？",
                "Evidence Pack 为什么要显示拒写边界？",
                "导出前 privacy scan 检查什么？",
                "HTML Evidence Pack 和 Markdown Evidence Pack 有什么用途？",
                "能力雷达如何计算证据强度？",
                "项目证据页应该展示哪些字段？",
                "面试防守卡如何引用证据？",
                "缺口路线图为什么不能写成已完成经历？",
                "Portfolio 是否可以包含无 source 的 claim？",
                "Traceability Appendix 有什么作用？",
                "Evidence Pack 能否暴露 DEEPSEEK_API_KEY？",
                "Portfolio export 为什么暂不阻塞 PDF？",
                "可分享 Evidence Pack 的第一屏应该说明什么？",
                "Portfolio 导出如何处理 local:// source？",
                "Evidence Pack 如何避免把弱证据夸大？"
        );
        addAnswered(cases, "portfolio", portfolioPrompts);
    }

    private void addAnswered(List<EvalCase> cases, String prefix, List<String> prompts) {
        for (String prompt : prompts) {
            add(cases, prefix + "_SHOULD_ANSWER", prompt, "ANSWERED");
        }
    }

    private void add(List<EvalCase> cases, String category, String question, String expectedStatus) {
        add(cases, category, question, expectedStatus, List.of(), List.of());
    }

    private void add(
            List<EvalCase> cases,
            String category,
            String question,
            String expectedStatus,
            List<String> requiredTerms,
            List<String> forbiddenTerms
    ) {
        cases.add(new EvalCase(UUID.randomUUID(), category, question, expectedStatus, requiredTerms, forbiddenTerms));
    }
}
