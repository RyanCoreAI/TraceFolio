package com.yanxai.tracefolio.gaps;

import java.util.List;

public record GapProjectPlan(
        String requirementId,
        String capability,
        String searchQuery,
        List<ReferenceProject> referenceProjects,
        String projectName,
        String objective,
        List<String> scope,
        List<String> acceptanceCriteria,
        List<String> readmeEvidenceToProduce,
        String allowedResumeWordingAfterDone,
        String stillCannotClaim,
        String scoutWarning
) {
}
