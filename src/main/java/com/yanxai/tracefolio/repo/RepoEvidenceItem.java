package com.yanxai.tracefolio.repo;

import java.util.List;

public record RepoEvidenceItem(
        String capability,
        String technology,
        String filePath,
        Integer lineNumber,
        String sourceUri,
        String quote,
        String strength,
        String riskLevel,
        List<String> signals,
        String fileHash,
        String symbolType,
        String symbolName,
        String sourceKind
) {
    public RepoEvidenceItem(
            String capability,
            String technology,
            String filePath,
            Integer lineNumber,
            String sourceUri,
            String quote,
            String strength,
            String riskLevel,
            List<String> signals
    ) {
        this(capability, technology, filePath, lineNumber, sourceUri, quote, strength, riskLevel, signals, "", "", "", "FILE");
    }
}
