package com.yanxai.tracefolio.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class HeadingAwareChunkerTest {

    private final HeadingAwareChunker chunker = new HeadingAwareChunker();

    @Test
    void preservesHeadingPath() {
        String markdown = """
                # Project
                %s
                ## Architecture
                %s
                """.formatted("intro ".repeat(80), "architecture ".repeat(80));

        List<ChunkDraft> chunks = chunker.chunk("README", markdown);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks)
                .extracting(ChunkDraft::headingPath)
                .anyMatch(path -> path.contains("Project"));
    }
}
