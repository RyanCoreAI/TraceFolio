package com.yanxai.tracefolio.source;

import java.util.UUID;

public record IngestResponse(
        UUID documentId,
        int chunkCount,
        String sourceType,
        String sourceUri
) {
}
