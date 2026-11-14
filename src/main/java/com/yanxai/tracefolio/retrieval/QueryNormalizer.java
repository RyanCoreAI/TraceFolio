package com.yanxai.tracefolio.retrieval;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QueryNormalizer {

    private static final Map<String, List<String>> ALIASES = new LinkedHashMap<>();

    static {
        ALIASES.put("patchbrake", List.of("AI-generated diff risk", "AI 编程安全", "diff 风险"));
        ALIASES.put("token studio", List.of("AI 编程 ROI", "复盘", "token 成本"));
        ALIASES.put("tracefolio", List.of("个人技术资产证据库", "EvidenceClaim", "trace replay", "可信 RAG"));
        ALIASES.put("jd", List.of("岗位要求", "招聘描述", "resume", "简历"));
        ALIASES.put("rag", List.of("检索增强生成", "retrieval", "citation", "引用", "证据链"));
        ALIASES.put("pgvector", List.of("PostgreSQL vector", "向量检索", "embedding"));
        ALIASES.put("spring ai", List.of("Spring AI", "AI 应用工程", "Tool Calling"));
        ALIASES.put("agent", List.of("工作流", "tool calling", "human-in-the-loop", "可审计编排"));
        ALIASES.put("作品集", List.of("portfolio", "Evidence Pack", "技术资产"));
        ALIASES.put("简历", List.of("resume", "JD Evidence Compiler", "拒写"));
    }

    public NormalizedQuery normalize(String question) {
        String original = question == null ? "" : question.strip();
        String lower = original.toLowerCase(Locale.ROOT);
        List<String> expansions = ALIASES.entrySet().stream()
                .filter(entry -> lower.contains(entry.getKey()) || original.contains(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .distinct()
                .limit(18)
                .toList();
        String expanded = expansions.isEmpty()
                ? original
                : (original + " " + String.join(" ", expansions)).strip();
        return new NormalizedQuery(original, expanded, expansions);
    }

    public record NormalizedQuery(
            String original,
            String expanded,
            List<String> expansions
    ) {
    }
}
