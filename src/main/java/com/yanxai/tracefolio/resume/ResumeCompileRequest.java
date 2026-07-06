package com.yanxai.tracefolio.resume;

import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import java.util.List;
import java.util.UUID;

public record ResumeCompileRequest(
        UUID runId,
        List<EvidenceMatrixRow> matrix
) {
}
