package com.yanxai.tracefolio.source;

import java.util.Map;
import java.util.UUID;

public record RawDocument(
        UUID id,
        String sourceType,
        String title,
        String sourceUri,
        String content,
        Map<String, Object> metadata
) {
}
