package com.yanxai.tracefolio.document;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DocumentSummary(
        UUID documentId,
        String sourceType,
        String title,
        String sourceUri,
        String contentHash,
        int chunkCount,
        OffsetDateTime capturedAt,
        OffsetDateTime createdAt
) {
}
