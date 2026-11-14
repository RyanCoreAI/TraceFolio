package com.yanxai.tracefolio.document;

import org.springframework.stereotype.Component;

@Component
public class MarkdownCleaner {

    public String clean(String markdown) {
        if (markdown == null) {
            return "";
        }

        return markdown
                .replace("\r\n", "\n")
                .replaceFirst("(?s)^---\\s*.*?---\\s*", "")
                .replaceAll("(?s)<!--.*?-->", "")
                .replaceAll("!\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
