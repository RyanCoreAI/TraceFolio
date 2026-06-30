package com.yanxai.tracefolio.retrieval;

import java.util.List;

public record RetrievalResult(
        List<RetrievedChunk> chunks,
        RetrievalSummary summary
) {
}
