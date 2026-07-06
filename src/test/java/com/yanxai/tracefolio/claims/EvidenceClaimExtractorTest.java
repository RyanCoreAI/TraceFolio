package com.yanxai.tracefolio.claims;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvidenceClaimExtractorTest {

    @Test
    void extractsClaimThatLinksBackToChunkAndOriginalQuote() {
        UUID chunkId = UUID.randomUUID();
        String content = """
                TraceFolio 使用 Spring Boot 实现 /api/qa，并通过 PGVector 保存 embedding vector。
                工作流会记录 trace、RRF 分数和引用校验结果，避免无证据强答。
                """;
        SourceChunk chunk = new SourceChunk(
                chunkId,
                UUID.randomUUID(),
                "GITHUB_README",
                "https://github.com/RyanCoreAI/TraceFolio/blob/main/README.md",
                "RyanCoreAI/TraceFolio README.md",
                "TraceFolio",
                content,
                Map.of("owner", "RyanCoreAI", "repo", "TraceFolio"),
                OffsetDateTime.now()
        );

        List<EvidenceClaimDraft> claims = new EvidenceClaimExtractor(null).extractFromChunk(chunk);

        assertThat(claims).isNotEmpty();
        assertThat(claims).allSatisfy(claim -> {
            assertThat(claim.sourceChunkId()).isEqualTo(chunkId);
            assertThat(claim.quote()).isNotBlank();
            assertThat(content).contains(claim.quote());
            assertThat(claim.sourceUri()).contains("TraceFolio");
        });
        assertThat(claims)
                .extracting(EvidenceClaimDraft::technology)
                .containsAnyOf("Spring Boot", "PGVector", "Trace");
    }
}
