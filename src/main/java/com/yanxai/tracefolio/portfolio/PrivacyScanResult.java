package com.yanxai.tracefolio.portfolio;

import java.util.List;

public record PrivacyScanResult(
        String status,
        List<String> issues
) {
    public boolean passed() {
        return "PASS".equals(status);
    }
}
