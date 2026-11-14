package com.yanxai.tracefolio.jd;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdRequirementParserTest {

    private final JdRequirementParser parser = new JdRequirementParser();

    @Test
    void parsesAtomicRequirementsAndClassifiesRisk() {
        String jd = """
                岗位职责：
                - 熟悉 Java、Spring Boot、Spring AI 和 RAG。
                - 需要能独立完成 Docker 部署、监控和问题复盘。
                - 有千万级生产系统经验优先。
                """;

        var requirements = parser.parse(jd);

        assertThat(requirements).hasSizeGreaterThanOrEqualTo(3);
        assertThat(requirements).extracting(JdRequirement::description)
                .anyMatch(item -> item.contains("Spring AI"));
        assertThat(requirements).extracting(JdRequirement::riskLevel)
                .contains("HIGH");
    }
}
