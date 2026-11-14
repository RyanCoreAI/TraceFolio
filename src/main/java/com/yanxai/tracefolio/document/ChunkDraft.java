package com.yanxai.tracefolio.document;

import java.util.Map;

public record ChunkDraft(
        int chunkIndex,
        String title,
        String headingPath,
        String content,
        String lineAnchor,
        Map<String, Object> metadata
) {
}
