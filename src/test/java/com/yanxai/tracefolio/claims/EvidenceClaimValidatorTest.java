package com.yanxai.tracefolio.claims;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceClaimValidatorTest {

    private final EvidenceClaimValidator validator = new EvidenceClaimValidator();

    @Test
    void marksMissingQuoteAsInvalidAndOverclaimAsNeedsReview() {
        EvidenceClaimDraft invalid = claim("", "实现普通项目能力", "LOW", "STRONG");
        assertThat(validator.validate(invalid).status()).isEqualTo("INVALID");

        EvidenceClaimDraft needsReview = claim(
                "TraceFolio implements evidence-backed RAG.",
                "主导千万级生产系统",
                "HIGH",
                "WEAK"
        );
        var result = validator.validate(needsReview);

        assertThat(result.status()).isEqualTo("NEEDS_REVIEW");
        assertThat(result.warnings()).isNotEmpty();
    }

    private EvidenceClaimDraft claim(String quote, String action, String risk, String strength) {
        return new EvidenceClaimDraft(
                "CHUNK",
                UUID.randomUUID(),
                null,
                null,
                "TECHNICAL_CAPABILITY",
                "TraceFolio",
                "可信 RAG",
                action,
                "Spring AI",
                "TraceFolio",
                "证据链",
                "GITHUB_README",
                "TraceFolio README",
                "https://github.com/RyanCoreAI/TraceFolio",
                "README",
                quote,
                strength,
                risk,
                0.8,
                "TEST",
                Map.of()
        );
    }
}
