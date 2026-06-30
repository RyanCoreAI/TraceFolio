package com.yanxai.tracefolio.qa;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AbstentionPolicy {

    private final TraceFolioProperties properties;

    public AbstentionPolicy(TraceFolioProperties properties) {
        this.properties = properties;
    }

    public Optional<String> shouldAbstainBeforeGeneration(List<RetrievedChunk> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return Optional.of("NO_EVIDENCE");
        }

        if (evidence.size() < properties.retrieval().minSelectedChunks()) {
            return Optional.of("INSUFFICIENT_EVIDENCE");
        }

        return Optional.empty();
    }

    public Optional<String> shouldAbstainAfterGeneration(GroundedAnswer answer) {
        if ("ABSTAINED".equals(answer.status())) {
            return Optional.of(answer.abstentionReason() == null ? "MODEL_ABSTAINED" : answer.abstentionReason());
        }

        if (!answer.unsupportedClaimsOrEmpty().isEmpty()) {
            return Optional.of("UNSUPPORTED_CORE_CLAIMS");
        }

        return Optional.empty();
    }
}
