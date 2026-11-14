package com.yanxai.tracefolio.retrieval;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RrfRanker {

    private final TraceFolioProperties properties;

    public RrfRanker(TraceFolioProperties properties) {
        this.properties = properties;
    }

    public List<RetrievedChunk> rank(
            List<RetrievalCandidate> vectorHits,
            List<RetrievalCandidate> ftsHits,
            int finalTopK
    ) {
        Map<UUID, MutableRank> ranks = new LinkedHashMap<>();

        for (RetrievalCandidate hit : vectorHits) {
            ranks.computeIfAbsent(hit.id(), id -> MutableRank.from(hit))
                    .withVector(hit.rank(), hit.score());
        }

        for (RetrievalCandidate hit : ftsHits) {
            ranks.computeIfAbsent(hit.id(), id -> MutableRank.from(hit))
                    .withFts(hit.rank(), hit.score());
        }

        List<MutableRank> merged = ranks.values().stream()
                .peek(rank -> rank.rrfScore = score(rank.vectorRank) + score(rank.ftsRank))
                .sorted(Comparator.comparingDouble(MutableRank::rrfScore).reversed())
                .limit(finalTopK)
                .toList();

        List<RetrievedChunk> result = new ArrayList<>();
        for (int i = 0; i < merged.size(); i++) {
            result.add(merged.get(i).toRetrievedChunk(i + 1));
        }
        return result;
    }

    private double score(Integer rank) {
        if (rank == null) {
            return 0.0;
        }
        return 1.0 / (properties.retrieval().rrfK() + rank);
    }

    private static class MutableRank {
        private final RetrievalCandidate candidate;
        private Integer vectorRank;
        private Integer ftsRank;
        private Double vectorScore;
        private Double ftsScore;
        private double rrfScore;

        private MutableRank(RetrievalCandidate candidate) {
            this.candidate = candidate;
        }

        static MutableRank from(RetrievalCandidate candidate) {
            return new MutableRank(candidate);
        }

        MutableRank withVector(int rank, double score) {
            this.vectorRank = rank;
            this.vectorScore = score;
            return this;
        }

        MutableRank withFts(int rank, double score) {
            this.ftsRank = rank;
            this.ftsScore = score;
            return this;
        }

        double rrfScore() {
            return rrfScore;
        }

        RetrievedChunk toRetrievedChunk(int finalRank) {
            return new RetrievedChunk(
                    candidate.id(),
                    candidate.documentId(),
                    candidate.sourceType(),
                    candidate.sourceUri(),
                    candidate.title(),
                    candidate.headingPath(),
                    candidate.content(),
                    candidate.metadata(),
                    vectorRank,
                    ftsRank,
                    finalRank,
                    vectorScore,
                    ftsScore,
                    rrfScore
            );
        }
    }
}
