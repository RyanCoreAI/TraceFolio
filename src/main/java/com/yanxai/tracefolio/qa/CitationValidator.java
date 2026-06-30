package com.yanxai.tracefolio.qa;

import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CitationValidator {

    public CitationValidationResult validate(GroundedAnswer answer, List<RetrievedChunk> evidence) {
        List<String> errors = new ArrayList<>();

        if ("ANSWERED".equals(answer.status()) && answer.citationsOrEmpty().isEmpty()) {
            errors.add("ANSWERED response must contain at least one citation.");
        }

        Map<UUID, RetrievedChunk> evidenceById = evidence.stream()
                .collect(Collectors.toMap(RetrievedChunk::id, Function.identity()));

        for (Citation citation : answer.citationsOrEmpty()) {
            RetrievedChunk chunk = evidenceById.get(citation.chunkId());
            if (chunk == null) {
                errors.add("Citation chunkId not found in evidence: " + citation.chunkId());
                continue;
            }

            if (citation.quote() == null || citation.quote().isBlank()) {
                errors.add("Citation quote is blank: " + citation.chunkId());
                continue;
            }

            if (!normalize(chunk.content()).contains(normalize(citation.quote()))) {
                errors.add("Citation quote not found in chunk: " + citation.chunkId());
            }
        }

        return new CitationValidationResult(errors.isEmpty(), errors);
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
