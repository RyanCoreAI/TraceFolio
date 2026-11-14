package com.yanxai.tracefolio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yanxai.tracefolio.claims.EvidenceClaim;
import com.yanxai.tracefolio.claims.EvidenceClaimRepository;
import com.yanxai.tracefolio.gaps.GapProjectPlan;
import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.JdRunRepository;
import com.yanxai.tracefolio.jd.JdRunView;
import com.yanxai.tracefolio.jd.MatchedEvidence;
import com.yanxai.tracefolio.resume.InterviewDefense;
import com.yanxai.tracefolio.resume.ProjectExperience;
import com.yanxai.tracefolio.resume.ResumeDraft;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioExportServiceTest {

    @Test
    void exportsFullEvidencePackSectionsAndMetadata() {
        EvidenceClaimRepository claimRepository = mock(EvidenceClaimRepository.class);
        JdRunRepository jdRunRepository = mock(JdRunRepository.class);
        PortfolioExportRepository exportRepository = mock(PortfolioExportRepository.class);
        UUID jdRunId = UUID.randomUUID();
        when(claimRepository.list(null, null, null, null, 30)).thenReturn(List.of(claim("STRONG")));
        when(jdRunRepository.find(jdRunId)).thenReturn(jdRun(jdRunId));

        PortfolioExportResponse response = new PortfolioExportService(
                claimRepository,
                jdRunRepository,
                exportRepository
        ).export(new PortfolioExportRequest("TraceFolio Pack", "MARKDOWN", jdRunId, 30));

        assertThat(response.content()).contains(
                "## 能力雷达",
                "## JD 匹配包",
                "## 岗位定制简历草稿",
                "## 面试防守卡",
                "## 缺口补证据路线图",
                "## 使用边界"
        );
        assertThat(response.metadata())
                .containsEntry("claimCount", 1)
                .containsEntry("capabilityCount", 1)
                .containsEntry("evidenceScore", 100)
                .containsEntry("matrixRows", 1)
                .containsEntry("gapProjectCount", 1);
        verify(exportRepository).save(any(PortfolioExportResponse.class));
    }

    private EvidenceClaim claim(String strength) {
        return new EvidenceClaim(
                UUID.randomUUID(),
                "REPO_SCAN",
                null,
                UUID.randomUUID(),
                "src/main/java/App.java",
                "TECHNICAL_CAPABILITY",
                "TraceFolio",
                "可信 RAG",
                "实现了 citation validator 和 trace replay",
                "Spring AI",
                "TraceFolio",
                "可追溯问答",
                "LOCAL_REPO_FILE",
                "TraceFolio README",
                "local://RyanCoreAI/TraceFolio/README.md#L12",
                "README",
                "TraceFolio implements trace replay and citation validation.",
                strength,
                "LOW",
                0.93,
                "repo_scan",
                Map.of("line", 12),
                OffsetDateTime.now()
        );
    }

    private JdRunView jdRun(UUID runId) {
        MatchedEvidence evidence = new MatchedEvidence(
                UUID.randomUUID(),
                "LOCAL_REPO_FILE",
                "TraceFolio README",
                "local://RyanCoreAI/TraceFolio/README.md#L12",
                "README",
                "TraceFolio implements trace replay and citation validation.",
                List.of("RAG"),
                1,
                1,
                null,
                0.016
        );
        EvidenceMatrixRow row = new EvidenceMatrixRow(
                "REQ-001",
                "hard_skill",
                "熟悉 RAG 和引用校验",
                "must_have",
                "SUPPORTED",
                0.91,
                "基于 TraceFolio 实现可信 RAG、引用校验和 trace replay。",
                "",
                List.of(evidence)
        );
        ResumeDraft draft = new ResumeDraft(
                List.of("可信 RAG / Spring AI / PGVector"),
                List.of(new ProjectExperience("TraceFolio", "local://RyanCoreAI/TraceFolio/README.md#L12", List.of("实现可回放证据链。"))),
                List.of("重视证据边界和可验证表达。"),
                List.of(new InterviewDefense("你怎么保证回答可信？", "回答必须带引用并通过 quote 校验。", "不能声称生产规模。", List.of(evidence))),
                "没有正式工作证据时，不生成伪造工作经历。"
        );
        GapProjectPlan gap = new GapProjectPlan(
                "REQ-002",
                "Observability",
                "spring boot observability language:Java",
                List.of(),
                "TraceFolio Observability Slice",
                "补齐指标、日志、健康检查。",
                List.of("新增 actuator health"),
                List.of("README 展示指标截图"),
                List.of("README 记录验收"),
                "实现 Spring Boot 可观测性最小闭环。",
                "仍不能写千万级生产系统。",
                "stars 只能表示热度。"
        );
        return new JdRunView(runId, "招聘 RAG 后端", List.of(), List.of(row), draft, List.of(gap), OffsetDateTime.now(), OffsetDateTime.now());
    }
}
