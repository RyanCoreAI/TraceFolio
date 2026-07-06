package com.yanxai.tracefolio.resume;

import java.util.List;

public record ResumeDraft(
        List<String> skillSummary,
        List<ProjectExperience> projectExperiences,
        List<String> selfEvaluation,
        List<InterviewDefense> interviewDefense,
        String workExperienceNotice
) {
}
