package com.yanxai.tracefolio.resume;

import java.util.List;

public record ProjectExperience(
        String projectName,
        String sourceUri,
        List<String> bullets
) {
}
