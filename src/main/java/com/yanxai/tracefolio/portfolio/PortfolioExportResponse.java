package com.yanxai.tracefolio.portfolio;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record PortfolioExportResponse(
        UUID exportId,
        String format,
        String title,
        String content,
        Map<String, Object> metadata,
        OffsetDateTime createdAt
) {
}
