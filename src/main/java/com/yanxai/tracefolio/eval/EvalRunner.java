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
        List<EvalCase> cases = defaultCases();
        UUID runId = UUID.randomUUID();
        int passed = 0;
        int failed = 0;
        List<String> failures = new ArrayList<>();

        createRun(runId, cases.size());
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
        }

        updateRun(runId, passed, failed, failures);
        return new EvalRunResult(runId, cases.size(), passed, failed, failures);
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

    private void createRun(UUID runId, int total) {
        jdbcTemplate.update("""
                INSERT INTO rag_eval_run (id, tenant_id, total_cases, passed_cases, failed_cases, summary)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """,
                runId,
                properties.tenantId(),
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
        return cases;
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
