package com.yanxai.tracefolio.resume;

import java.util.List;
import java.util.UUID;

public record ResumeCompileResponse(
        UUID runId,
        ResumeDraft draft,
        List<ResumeClaim> claims,
        List<String> refusedClaims
) {
}
