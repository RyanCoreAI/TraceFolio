package com.yanxai.tracefolio.document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class HeadingAwareChunker {

    private static final int MAX_CHARS = 1800;
    private static final int MIN_CHARS = 300;
    private static final Pattern HEADING_PATTERN = Pattern.compile("^(#{1,6})\\s+(.+)$");

    public List<ChunkDraft> chunk(String title, String markdown) {
        List<ChunkDraft> chunks = new ArrayList<>();
        List<String> headingStack = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String currentHeadingPath = title;
        int chunkIndex = 0;
        int startLine = 1;

        String[] lines = markdown.split("\\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Heading heading = parseHeading(line);

            if (heading != null && buffer.length() >= MIN_CHARS) {
                chunks.add(toChunk(chunkIndex++, title, currentHeadingPath, buffer, startLine));
                buffer.setLength(0);
                startLine = i + 1;
            }

            if (heading != null) {
                while (headingStack.size() >= heading.level()) {
                    headingStack.removeLast();
                }
                headingStack.add(heading.text());
                currentHeadingPath = title + " > " + String.join(" > ", headingStack);
            }

            buffer.append(line).append('\n');

            if (buffer.length() >= MAX_CHARS) {
                chunks.add(toChunk(chunkIndex++, title, currentHeadingPath, buffer, startLine));
                buffer.setLength(0);
                startLine = i + 2;
            }
        }

        if (!buffer.toString().isBlank()) {
            chunks.add(toChunk(chunkIndex, title, currentHeadingPath, buffer, startLine));
        }

        return chunks;
    }

    private ChunkDraft toChunk(int index, String title, String headingPath, StringBuilder buffer, int startLine) {
        String content = buffer.toString().trim();
        return new ChunkDraft(
                index,
                title,
                headingPath,
                content,
                "L" + startLine,
                Map.of("charCount", content.length())
        );
    }

    private Heading parseHeading(String line) {
        Matcher matcher = HEADING_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        return new Heading(matcher.group(1).length(), matcher.group(2).trim());
    }

    private record Heading(int level, String text) {
    }
}
