package com.yanxai.tracefolio.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RrfRankerTest {

    @Test
    void boostsChunksAppearingInBothChannels() {
        TraceFolioProperties properties = new TraceFolioProperties(
                "ryan",
                null,
                null,
                null,
                new TraceFolioProperties.Retrieval(10, 10, 3, 60, 2),
                null
        );
        RrfRanker ranker = new RrfRanker(properties);
        UUID shared = UUID.randomUUID();
        UUID vectorOnly = UUID.randomUUID();

        List<RetrievedChunk> result = ranker.rank(
                List.of(candidate(shared, 2, "VECTOR"), candidate(vectorOnly, 1, "VECTOR")),
                List.of(candidate(shared, 1, "FTS")),
                2
        );

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().id()).isEqualTo(shared);
        assertThat(result.getFirst().vectorRank()).isEqualTo(2);
        assertThat(result.getFirst().ftsRank()).isEqualTo(1);
    }

    private RetrievalCandidate candidate(UUID id, int rank, String channel) {
        return new RetrievalCandidate(
                id,
                UUID.randomUUID(),
                "BLOG",
                "https://example.com",
                "title",
                "heading",
                "content",
                Map.of(),
                rank,
                channel,
                1.0
        );
    }
}
