package com.yanxai.tracefolio.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class WorkflowResumeRequestTest {

    @Test
    void keepsResumeAndRetryMetadataSafeWhenMissing() {
        assertThat(new WorkflowResumeRequest("ok", null).metadataOrEmpty()).isEmpty();
        assertThat(new WorkflowRetryRequest("retry", null).overrideInputOrEmpty()).isEmpty();
        assertThat(new WorkflowResumeRequest("ok", Map.of("reviewer", "Ryan")).metadataOrEmpty())
                .containsEntry("reviewer", "Ryan");
    }
}
