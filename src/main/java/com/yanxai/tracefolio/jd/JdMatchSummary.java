package com.yanxai.tracefolio.jd;

public record JdMatchSummary(
        int totalRequirements,
        int supported,
        int weaklySupported,
        int unsupported,
        int gapProjectRequired
) {
}
