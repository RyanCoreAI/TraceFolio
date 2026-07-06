package com.yanxai.tracefolio.resume;

import com.yanxai.tracefolio.jd.MatchedEvidence;
import java.util.List;

public record InterviewDefense(
        String possibleQuestion,
        String defensibleAnswer,
        String boundary,
        List<MatchedEvidence> evidence
) {
}
