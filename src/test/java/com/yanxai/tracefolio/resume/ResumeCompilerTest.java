package com.yanxai.tracefolio.resume;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.MatchedEvidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResumeCompilerTest {

    private final ResumeCompiler compiler = new ResumeCompiler();

    @Test
    void compilesOnlySupportedClaimsAndRefusesUnsupportedClaims() {
        EvidenceMatrixRow supported = new EvidenceMatrixRow(
                "REQ-001",
                "技术栈",
                "熟悉 Spring AI RAG",
                "MUST_HAVE",
                "SUPPORTED",
                0.8,
                "可写",
                "",
                List.of(new MatchedEvidence(
                        UUID.randomUUID(),
                        "GITHUB_README",
                        "TraceFolio",
                        "https://example.com",
                        "README",
                        "Spring AI + PGVector 可信 RAG 实战",
                        List.of("Spring AI", "RAG"),
                        1,
                        1,
                        null,
                        0.1
                ))
        );
        EvidenceMatrixRow unsupported = new EvidenceMatrixRow(
                "REQ-002",
                "经验年限",
                "主导千万级生产系统",
                "MUST_HAVE",
                "GAP_PROJECT_REQUIRED",
                0,
                "暂不写入简历",
                "缺少生产系统证据。",
                List.of()
        );

        ResumeCompileResponse response = compiler.compile(UUID.randomUUID(), List.of(supported, unsupported));

        assertThat(response.claims()).hasSize(1);
        assertThat(response.refusedClaims()).hasSize(1);
        assertThat(response.draft().projectExperiences()).hasSize(1);
        assertThat(response.draft().workExperienceNotice()).contains("不生成工作经历");
        assertThat(response.refusedClaims().getFirst()).contains("主导千万级生产系统");
    }
}
