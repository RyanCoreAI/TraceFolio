package com.yanxai.tracefolio.claims;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

record SourceChunk(
        UUID id,
        UUID documentId,
        String sourceType,
        String sourceUri,
        String title,
        String headingPath,
        String content,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
