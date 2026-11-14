package com.yanxai.tracefolio.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WorkflowRunRequestTest {

    @Test
    void keepsWorkflowDefaultsConservative() {
        WorkflowRunRequest request = new WorkflowRunRequest("Java RAG JD", null, null);

        assertThat(request.safeTargetDays()).isEqualTo(5);
        assertThat(request.shouldExtractClaims()).isTrue();
        assertThat(new WorkflowRunRequest("JD", 99, false).safeTargetDays()).isEqualTo(7);
        assertThat(new WorkflowRunRequest("JD", 1, false).safeTargetDays()).isEqualTo(3);
        assertThat(new WorkflowRunRequest("JD", 5, false).shouldExtractClaims()).isFalse();
    }
}
