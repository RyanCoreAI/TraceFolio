package com.yanxai.tracefolio.claims;

import java.util.List;

public record ClaimExtractResponse(
        int scannedChunks,
        int generatedClaims,
        int insertedClaims,
        int skippedDuplicates,
        List<EvidenceClaim> sampleClaims
) {
}
