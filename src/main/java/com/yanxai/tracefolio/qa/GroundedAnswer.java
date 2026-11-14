package com.yanxai.tracefolio.qa;

import java.util.List;

public record GroundedAnswer(
        String status,
        String answer,
        List<Citation> citations,
        List<String> unsupportedClaims,
        String abstentionReason
) {
    public List<Citation> citationsOrEmpty() {
        return citations == null ? List.of() : citations;
    }

    public List<String> unsupportedClaimsOrEmpty() {
        return unsupportedClaims == null ? List.of() : unsupportedClaims;
    }
}
