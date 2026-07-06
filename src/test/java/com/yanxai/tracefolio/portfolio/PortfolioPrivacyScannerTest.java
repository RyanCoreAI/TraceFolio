package com.yanxai.tracefolio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortfolioPrivacyScannerTest {

    private final PortfolioPrivacyScanner scanner = new PortfolioPrivacyScanner();

    @Test
    void redactsLocalPathsAndSecretLikeAssignments() {
        String content = "path=D:\\TraceFolio\\README.md\nDEEPSEEK_API_KEY=sk-test\npassword=abc123";

        PrivacyScanResult result = scanner.scan(content);
        String redacted = scanner.redact(content);

        assertThat(result.status()).isEqualTo("REDACTED");
        assertThat(redacted).doesNotContain("D:\\TraceFolio");
        assertThat(redacted).doesNotContain("sk-test");
        assertThat(redacted).contains("[REDACTED]");
    }
}
