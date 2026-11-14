package com.yanxai.tracefolio.claims;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class EvidenceClaimExtractor {

    private static final List<CapabilityRule> RULES = List.of(
            new CapabilityRule("可信 RAG", "RAG", "检索增强生成", List.of("rag", "检索增强", "grounded answer", "citation", "引用校验", "拒答")),
            new CapabilityRule("Spring AI 集成", "Spring AI", "接入模型与向量能力", List.of("spring ai", "spring-ai")),
            new CapabilityRule("向量检索", "PGVector", "构建向量检索", List.of("pgvector", "vector store", "向量检索", "embedding vector")),
            new CapabilityRule("PostgreSQL 数据建模", "PostgreSQL", "设计证据数据模型", List.of("postgresql", "postgres", "tsvector", "full text search", "fts")),
            new CapabilityRule("数据库迁移", "Flyway", "维护数据库迁移", List.of("flyway", "migration", "db/migration")),
            new CapabilityRule("后端 API 设计", "Spring Boot", "实现后端接口", List.of("spring boot", "@restcontroller", "/api/", "controller")),
            new CapabilityRule("前端工作台", "Vue", "构建前端工作台", List.of("vue", "vite", "frontend")),
            new CapabilityRule("容器化运行", "Docker", "配置本地运行环境", List.of("docker", "docker compose", "docker-compose")),
            new CapabilityRule("模型接入", "DeepSeek", "接入聊天模型", List.of("deepseek", "json output", "response_format")),
            new CapabilityRule("本地 embedding", "Ollama", "接入本地向量模型", List.of("ollama", "bge-m3")),
            new CapabilityRule("自动评测", "Eval", "设计回归评测", List.of("eval", "golden", "评测", "test plan")),
            new CapabilityRule("Trace 回放", "Trace", "记录可回放证据链", List.of("trace", "回放", "retrieval trace", "rrf")),
            new CapabilityRule("Agent 编排", "Agent", "规划可审计工作流", List.of("agent", "workflow", "human-in-the-loop", "tool calling")),
            new CapabilityRule("鉴权与安全", "Security", "实现安全边界", List.of("jwt", "auth", "permission", "安全", "权限")),
            new CapabilityRule("缓存与限流", "Redis", "实现缓存或限流能力", List.of("redis", "rate limit", "限流"))
    );

    private static final Pattern STRONG_VERBS = Pattern.compile(
            "(实现|接入|配置|新增|构建|搭建|部署|校验|评测|回放|支持|设计|集成|维护|验证|implement|implemented|build|built|configure|configured|support|supports|deploy|test|validate)",
            Pattern.CASE_INSENSITIVE
    );

    private final EvidenceClaimRepository repository;

    public EvidenceClaimExtractor(EvidenceClaimRepository repository) {
        this.repository = repository;
    }

    public ClaimExtractResponse extract(ClaimExtractRequest request) {
        ClaimExtractRequest safeRequest = request == null ? new ClaimExtractRequest(false, 500) : request;
        if (safeRequest.shouldClearExisting()) {
            repository.deleteTenantClaims();
        }

        List<SourceChunk> chunks = repository.chunksForExtraction(safeRequest.safeLimit());
        int generated = 0;
        int inserted = 0;
        for (SourceChunk chunk : chunks) {
            List<EvidenceClaimDraft> claims = extractFromChunk(chunk);
            generated += claims.size();
            for (EvidenceClaimDraft claim : claims) {
                if (repository.insert(claim)) {
                    inserted++;
                }
            }
        }

        return new ClaimExtractResponse(
                chunks.size(),
                generated,
                inserted,
                generated - inserted,
                repository.list(null, null, null, null, 12)
        );
    }

    List<EvidenceClaimDraft> extractFromChunk(SourceChunk chunk) {
        if (chunk.content() == null || chunk.content().isBlank()) {
            return List.of();
        }
        String haystack = (chunk.title() + "\n" + chunk.headingPath() + "\n" + chunk.content()).toLowerCase(Locale.ROOT);
        List<EvidenceClaimDraft> claims = new ArrayList<>();
        for (CapabilityRule rule : RULES) {
            String keyword = firstMatchedKeyword(haystack, rule.keywords());
            if (keyword == null) {
                continue;
            }
            String quote = extractQuote(chunk.content(), keyword);
            if (quote.isBlank() || !chunk.content().contains(quote)) {
                continue;
            }
            String strength = strength(chunk, quote);
            claims.add(new EvidenceClaimDraft(
                    "CHUNK",
                    chunk.id(),
                    null,
                    null,
                    "CAPABILITY",
                    rule.capability(),
                    rule.capability(),
                    rule.action(),
                    rule.technology(),
                    projectName(chunk),
                    "公开资料中可证明“%s”相关能力。".formatted(rule.capability()),
                    chunk.sourceType(),
                    chunk.title(),
                    chunk.sourceUri(),
                    chunk.headingPath(),
                    quote,
                    strength,
                    riskLevel(rule, quote),
                    confidence(strength),
                    "RULE_BASED_CHUNK_CLAIM_V1",
                    metadata(chunk, keyword)
            ));
            if (claims.size() >= 6) {
                break;
            }
        }
        return claims;
    }

    private String firstMatchedKeyword(String haystack, List<String> keywords) {
        for (String keyword : keywords) {
            if (haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                return keyword;
            }
        }
        return null;
    }

    private String extractQuote(String content, String keyword) {
        String lower = content.toLowerCase(Locale.ROOT);
        int index = lower.indexOf(keyword.toLowerCase(Locale.ROOT));
        if (index < 0) {
            index = 0;
        }
        int start = Math.max(0, index - 120);
        int end = Math.min(content.length(), index + 220);
        start = nearestBoundary(content, start, index, true);
        end = nearestBoundary(content, end, index, false);
        return content.substring(start, end).strip();
    }

    private int nearestBoundary(String content, int cursor, int anchor, boolean backwards) {
        if (backwards) {
            for (int i = anchor; i >= cursor; i--) {
                if (isBoundary(content.charAt(i))) {
                    return Math.min(i + 1, content.length());
                }
            }
            return cursor;
        }
        for (int i = anchor; i < cursor; i++) {
            if (isBoundary(content.charAt(i))) {
                return i + 1;
            }
        }
        return cursor;
    }

    private boolean isBoundary(char c) {
        return c == '\n' || c == '。' || c == '！' || c == '？' || c == '.' || c == ';';
    }

    private String projectName(SourceChunk chunk) {
        Object repo = chunk.metadata().get("repo");
        Object owner = chunk.metadata().get("owner");
        if (owner != null && repo != null && !repo.toString().isBlank()) {
            return owner + "/" + repo;
        }
        String title = chunk.title() == null ? "" : chunk.title();
        if (title.contains(" README")) {
            return title.substring(0, title.indexOf(" README")).strip();
        }
        return title.isBlank() ? "公开技术资产" : title;
    }

    private String strength(SourceChunk chunk, String quote) {
        String sourceType = chunk.sourceType() == null ? "" : chunk.sourceType();
        if (STRONG_VERBS.matcher(quote).find() && (sourceType.contains("GITHUB") || quote.length() >= 40)) {
            return "STRONG";
        }
        if (STRONG_VERBS.matcher(quote).find()) {
            return "MEDIUM";
        }
        return "WEAK";
    }

    private String riskLevel(CapabilityRule rule, String quote) {
        String lower = quote.toLowerCase(Locale.ROOT);
        if (lower.contains("千万") || lower.contains("百万") || lower.contains("大厂")
                || lower.contains("commercial") || lower.contains("revenue") || lower.contains("生产级")) {
            return "HIGH";
        }
        if (List.of("Security", "Docker", "PostgreSQL").contains(rule.technology())) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private double confidence(String strength) {
        return switch (strength) {
            case "STRONG" -> 0.86;
            case "MEDIUM" -> 0.68;
            default -> 0.48;
        };
    }

    private Map<String, Object> metadata(SourceChunk chunk, String keyword) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("matchedKeyword", keyword);
        metadata.put("documentId", chunk.documentId());
        metadata.put("chunkCreatedAt", chunk.createdAt());
        return metadata;
    }

    private record CapabilityRule(
            String capability,
            String technology,
            String action,
            List<String> keywords
    ) {
    }
}
