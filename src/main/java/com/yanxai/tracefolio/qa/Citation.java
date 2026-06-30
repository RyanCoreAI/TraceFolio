package com.yanxai.tracefolio.qa;

import java.util.UUID;

public record Citation(
        UUID chunkId,
        String sourceType,
        String title,
        String sourceUri,
        String headingPath,
        String quote
) {
}
