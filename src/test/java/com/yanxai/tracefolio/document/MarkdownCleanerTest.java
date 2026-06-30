package com.yanxai.tracefolio.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MarkdownCleanerTest {

    private final MarkdownCleaner cleaner = new MarkdownCleaner();

    @Test
    void cleansFrontMatterLinksImagesAndComments() {
        String markdown = """
                ---
                title: Demo
                ---
                <!-- hidden -->
                # Hello
                [TraceFolio](https://example.com)
                ![Alt text](image.png)
                """;

        String cleaned = cleaner.clean(markdown);

        assertThat(cleaned).contains("# Hello");
        assertThat(cleaned).contains("TraceFolio");
        assertThat(cleaned).contains("Alt text");
        assertThat(cleaned).doesNotContain("title: Demo", "<!--", "https://example.com", "image.png");
    }
}
