package com.yanxai.tracefolio.jd;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class JdRequirementParser {

    private static final int MAX_REQUIREMENTS = 20;
    private static final Pattern BULLET_PREFIX = Pattern.compile("^[\\s\\-•*\\d.、)）]+");
    private static final List<String> TECH_KEYWORDS = List.of(
            "Java", "Spring Boot", "Spring AI", "RAG", "Agent", "PGVector", "PostgreSQL",
            "Redis", "JWT", "Docker", "Kubernetes", "CI/CD", "Observability", "OpenAI",
            "DeepSeek", "Ollama", "LLM", "Embedding", "Vector Search", "Full Text Search",
            "Tool Calling", "MCP", "MySQL", "Kafka", "RabbitMQ", "微服务", "高并发",
            "分布式", "权限", "安全", "监控", "部署", "测试", "前端", "Vue", "React"
    );

    public List<JdRequirement> parse(String jdText) {
        if (jdText == null || jdText.isBlank()) {
            return List.of();
        }

        List<String> candidates = splitCandidates(jdText);
        List<JdRequirement> requirements = new ArrayList<>();
        for (String candidate : candidates) {
            if (requirements.size() >= MAX_REQUIREMENTS) {
                break;
            }
            String normalized = normalize(candidate);
            if (normalized.length() < 6 || isSectionTitle(normalized)) {
                continue;
            }
            requirements.add(toRequirement(requirements.size() + 1, normalized));
        }

        addDetectedTechRequirements(jdText, requirements);
        return renumber(requirements);
    }

    private List<String> splitCandidates(String jdText) {
        Set<String> candidates = new LinkedHashSet<>();
        for (String line : jdText.split("\\R")) {
            String trimmed = normalize(line);
            if (!trimmed.isBlank()) {
                candidates.add(trimmed);
            }
            for (String part : trimmed.split("[。；;]")) {
                String value = normalize(part);
                if (!value.isBlank()) {
                    candidates.add(value);
                }
            }
        }
        return new ArrayList<>(candidates);
    }

    private void addDetectedTechRequirements(String jdText, List<JdRequirement> requirements) {
        String lowerText = jdText.toLowerCase(Locale.ROOT);
        for (String keyword : TECH_KEYWORDS) {
            if (requirements.size() >= MAX_REQUIREMENTS) {
                return;
            }
            String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
            if (!lowerText.contains(lowerKeyword)) {
                continue;
            }
            boolean alreadyCovered = requirements.stream()
                    .anyMatch(requirement -> containsIgnoreCase(requirement.description(), keyword));
            if (!alreadyCovered) {
                String description = "具备 " + keyword + " 相关项目实践或可解释证据";
                requirements.add(toRequirement(requirements.size() + 1, description));
            }
        }
    }

    private List<JdRequirement> renumber(List<JdRequirement> requirements) {
        List<JdRequirement> result = new ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            JdRequirement source = requirements.get(i);
            result.add(new JdRequirement(
                    "REQ-%03d".formatted(i + 1),
                    source.category(),
                    source.description(),
                    source.priority(),
                    source.evidenceNeeded(),
                    source.riskLevel(),
                    source.requirementType(),
                    source.hardGate(),
                    source.keywordsOrEmpty()
            ));
        }
        return result;
    }

    private JdRequirement toRequirement(int index, String description) {
        List<String> keywords = extractKeywords(description);
        String category = classifyCategory(description, keywords);
        String priority = classifyPriority(description);
        String risk = classifyRisk(description);
        String type = requirementType(description, category);
        return new JdRequirement(
                "REQ-%03d".formatted(index),
                category,
                description,
                priority,
                evidenceNeeded(category, risk),
                risk,
                type,
                "MUST_HAVE".equals(priority) && ("HIGH".equals(risk) || List.of("经验年限", "架构能力", "工程交付").contains(category)),
                keywords
        );
    }

    private List<String> extractKeywords(String description) {
        List<String> keywords = new ArrayList<>();
        for (String keyword : TECH_KEYWORDS) {
            if (containsIgnoreCase(description, keyword)) {
                keywords.add(keyword);
            }
        }

        for (String token : description.split("[,，、/\\s]+")) {
            String value = normalize(token);
            if (value.length() >= 2 && value.length() <= 24 && looksLikeSignal(value)) {
                keywords.add(value);
            }
        }
        return keywords.stream().distinct().limit(8).toList();
    }

    private String classifyCategory(String description, List<String> keywords) {
        String lower = description.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "年", "经验", "years", "年以上")) {
            return "经验年限";
        }
        if (containsAny(lower, "沟通", "协作", "团队", "表达", "推动")) {
            return "软技能";
        }
        if (containsAny(lower, "上线", "部署", "ci/cd", "交付", "监控", "观测", "observability")) {
            return "工程交付";
        }
        if (containsAny(lower, "架构", "设计", "高并发", "分布式", "安全", "权限")) {
            return "架构能力";
        }
        if (!keywords.isEmpty()) {
            return "技术栈";
        }
        return "岗位要求";
    }

    private String classifyPriority(String description) {
        String lower = description.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "加分", "优先", "nice", "plus", "bonus")) {
            return "NICE_TO_HAVE";
        }
        return "MUST_HAVE";
    }

    private String classifyRisk(String description) {
        String lower = description.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "主导", "负责", "生产", "千万", "亿级", "大规模", "高并发", "年以上", "years")) {
            return "HIGH";
        }
        if (containsAny(lower, "熟悉", "掌握", "上线", "部署", "经验")) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String requirementType(String description, String category) {
        String lower = description.toLowerCase(Locale.ROOT);
        if (containsAny(lower, "年", "经验", "years", "年以上")) {
            return "years";
        }
        if (containsAny(lower, "千万", "百万", "高并发", "大规模", "亿级")) {
            return "scale";
        }
        if (containsAny(lower, "行业", "业务", "电商", "金融", "saas", "跨境")) {
            return "business_domain";
        }
        return switch (category) {
            case "技术栈" -> "hard_skill";
            case "工程交付" -> "delivery";
            case "架构能力" -> "architecture";
            case "软技能" -> "soft_skill";
            default -> "general";
        };
    }

    private String evidenceNeeded(String category, String risk) {
        if ("HIGH".equals(risk)) {
            return "需要项目文档、README、复盘或上线记录中的明确证据，不能只靠关键词。";
        }
        return switch (category) {
            case "软技能" -> "需要协作、推动、沟通场景的复盘或项目记录。";
            case "工程交付" -> "需要部署、测试、监控、CI/CD 或运行说明证据。";
            case "架构能力" -> "需要架构设计、权衡说明、约束和结果证据。";
            default -> "需要公开技术资产中的项目实现、README、博客或复盘证据。";
        };
    }

    private boolean isSectionTitle(String value) {
        return value.length() <= 12 && containsAny(value, "岗位职责", "任职要求", "加分项", "职位描述", "要求");
    }

    private boolean looksLikeSignal(String value) {
        return TECH_KEYWORDS.stream().anyMatch(keyword -> containsIgnoreCase(value, keyword))
                || value.matches(".*[A-Za-z][A-Za-z0-9+#.-]*.*")
                || containsAny(value, "高并发", "分布式", "微服务", "权限", "安全", "监控", "部署", "测试", "架构", "项目");
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        return text != null && keyword != null && text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT));
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return BULLET_PREFIX.matcher(value.strip().replaceAll("\\s+", " ")).replaceFirst("").strip();
    }
}
