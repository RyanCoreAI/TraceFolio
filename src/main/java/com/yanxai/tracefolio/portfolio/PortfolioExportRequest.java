package com.yanxai.tracefolio.portfolio;

import java.util.UUID;

public record PortfolioExportRequest(
        String title,
        String format,
        UUID jdRunId,
        Integer maxClaims
) {
    public String safeTitle() {
        return title == null || title.isBlank() ? "TraceFolio Evidence Pack" : title.strip();
    }

    public String safeFormat() {
        return format == null || format.isBlank() ? "MARKDOWN" : format.strip().toUpperCase();
    }

    public int safeMaxClaims() {
        if (maxClaims == null || maxClaims <= 0) {
            return 30;
        }
        return Math.min(maxClaims, 120);
    }
}
