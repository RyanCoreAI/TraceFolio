package com.yanxai.tracefolio.qa;

import com.yanxai.tracefolio.retrieval.RetrievalSummary;
import java.util.List;
import java.util.UUID;

public record QaResponse(
        UUID traceId,
        String status,
        String answer,
        List<Citation> citations,
        List<String> unsupportedClaims,
        String abstentionReason,
        RetrievalSummary retrievalSummary
) {
}
