package com.yanxai.tracefolio.eval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class EvalRunRequestTest {

    @Test
    void normalizesSuiteTagsAndFailFast() {
        EvalRunRequest request = new EvalRunRequest(" ALL ", List.of(" RAG ", "rag", ""), true);

        assertThat(request.safeSuite()).isEqualTo("all");
        assertThat(request.tagsOrEmpty()).containsExactly("rag");
        assertThat(request.shouldFailFast()).isTrue();
        assertThat(new EvalRunRequest(null, null, null).safeSuite()).isEqualTo("core");
    }
}
