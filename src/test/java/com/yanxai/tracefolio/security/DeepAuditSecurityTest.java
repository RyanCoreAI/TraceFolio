package com.yanxai.tracefolio.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yanxai.tracefolio.claims.EvidenceClaimRepository;
import com.yanxai.tracefolio.jd.JdRunRepository;
import com.yanxai.tracefolio.portfolio.*;
import com.yanxai.tracefolio.qa.*;
import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class DeepAuditSecurityTest {
    @Test
    void htmlExportDoesNotExecuteImportedMarkupOrUnsafeLinks() {
        var claims = mock(EvidenceClaimRepository.class);
        when(claims.list(null, null, null, null, 30)).thenReturn(List.of());
        var service = new PortfolioExportService(claims, mock(JdRunRepository.class), mock(PortfolioExportRepository.class));
        var result = service.export(new PortfolioExportRequest(
                "<script>window.auditProbe=1</script><img src=x onerror=alert(1)> [open](javascript:alert%281%29)", "HTML", null, 30));
        var html = Jsoup.parse(result.content());
        assertThat(html.select("script, [onerror], a[href^=javascript:]")).isEmpty();
        assertThat(html.text()).contains("window.auditProbe");
    }

    @Test
    void unrecognizedModelStatusCannotBypassCitationGate() {
        var validator = new CitationValidator();
        assertThat(validator.validate(new GroundedAnswer("UNKNOWN", "unverified answer", List.of(), List.of(), null), List.of()).passed()).isFalse();
        assertThat(validator.validate(new GroundedAnswer(null, "unverified answer", List.of(), List.of(), null), List.of()).passed()).isFalse();
    }

    @Test
    void correctQuoteDoesNotAuthorizeFabricatedCitationDestination() {
        UUID id = UUID.randomUUID();
        var evidence = new RetrievedChunk(id, UUID.randomUUID(), "BLOG", "https://example.com/real", "real title", "section",
                "This is supported evidence.", Map.of(), 1, null, 1, 0.9, null, 0.1);
        var answer = new GroundedAnswer("ANSWERED", "supported evidence", List.of(new Citation(id, "BLOG", "real title",
                "https://example.net/fabricated", "section", "supported evidence")), List.of(), null);
        assertThat(new CitationValidator().validate(answer, List.of(evidence)).passed()).isFalse();
    }

    @Test
    void privacyScannerRedactsQuotedJsonSecretsIncludingSpaces() {
        var scanner = new PortfolioPrivacyScanner();
        String input = "{\"password\": \"synthetic private value\", \"token\": \"synthetic-token-value\"}";
        assertThat(scanner.scan(input).passed()).isFalse();
        assertThat(scanner.redact(input)).doesNotContain("synthetic", "private value");
    }

    @Test
    void privacyScannerRecognizesForwardSlashWindowsPaths() {
        var scanner = new PortfolioPrivacyScanner();
        String input = "local file D:/Private/README.md";
        assertThat(scanner.scan(input).passed()).isFalse();
        assertThat(scanner.redact(input)).doesNotContain("D:/Private");
    }

    @Test
    void privacyRedactionAlsoCoversHtmlAndPersistedResponseTitle() {
        var claims = mock(EvidenceClaimRepository.class);
        when(claims.list(null, null, null, null, 30)).thenReturn(List.of());
        var service = new PortfolioExportService(claims, mock(JdRunRepository.class), mock(PortfolioExportRepository.class));
        var result = service.export(new PortfolioExportRequest("GITHUB_TOKEN=synthetic-private-value", "HTML", null, 30));
        assertThat(result.metadata()).containsEntry("privacyStatus", "REDACTED");
        assertThat(result.content()).doesNotContain("synthetic-private-value");
        assertThat(result.title()).doesNotContain("synthetic-private-value");
    }
}
