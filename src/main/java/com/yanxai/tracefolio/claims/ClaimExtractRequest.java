package com.yanxai.tracefolio.claims;

public record ClaimExtractRequest(
        Boolean clearExisting,
        Integer limit
) {
    public boolean shouldClearExisting() {
        return Boolean.TRUE.equals(clearExisting);
    }

    public int safeLimit() {
        if (limit == null || limit <= 0) {
            return 500;
        }
        return Math.min(limit, 5000);
    }
}
