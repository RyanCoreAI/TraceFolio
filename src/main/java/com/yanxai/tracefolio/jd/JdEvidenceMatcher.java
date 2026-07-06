package com.yanxai.tracefolio.jd;

import com.yanxai.tracefolio.claims.EvidenceClaim;
import com.yanxai.tracefolio.claims.EvidenceClaimRepository;
import com.yanxai.tracefolio.retrieval.RetrievalResult;
import com.yanxai.tracefolio.retrieval.RetrievalService;
import com.yanxai.tracefolio.retrieval.RetrievedChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class JdEvidenceMatcher {

    private final RetrievalService retrievalService;
    private final EvidenceClaimRepository claimRepository;

    public JdEvidenceMatcher(RetrievalService retrievalService, EvidenceClaimRepository claimRepository) {
        this.retrievalService = retrievalService;
        this.claimRepository = claimRepository;
    }

    public JdMatchSummary summarize(List<EvidenceMatrixRow> matrix) {
        return new JdMatchSummary(
                matrix.size(),
                count(matrix, "SUPPORTED"),
                count(matrix, "WEAKLY_SUPPORTED"),
                count(matrix, "UNSUPPORTED"),
                count(matrix, "GAP_PROJECT_REQUIRED")
        );
    }

    public List<EvidenceMatrixRow> match(List<JdRequirement> requirements) {
        return requirements.stream()
                .map(this::matchRequirement)
                .toList();
    }

    private EvidenceMatrixRow matchRequirement(JdRequirement requirement) {
        List<MatchedEvidence> evidence = retrieveEvidence(requirement);
        double confidence = confidence(requirement, evidence);
        String status = supportStatus(requirement, evidence, confidence);
        return new EvidenceMatrixRow(
                requirement.id(),
                requirement.category(),
                requirement.description(),
                requirement.priority(),
                status,
                confidence,
                allowedResumeWording(requirement, status),
                forbiddenWording(requirement, status),
                scoreExplanation(requirement, evidence, confidence),
                missingGap(requirement, status),
                evidence
        );
    }

    private List<MatchedEvidence> retrieveEvidence(JdRequirement requirement) {
        List<MatchedEvidence> claimEvidence = matchClaims(requirement);
        if (!claimEvidence.isEmpty()) {
            return claimEvidence;
        }
        try {
            RetrievalResult result = retrievalService.retrieve(buildQuery(requirement));
            return result.chunks().stream()
                    .map(chunk -> toEvidence(requirement, chunk))
                    .filter(match -> !match.matchedKeywords().isEmpty())
                    .sorted(Comparator.comparingDouble(MatchedEvidence::rrfScore).reversed())
                    .limit(3)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<MatchedEvidence> matchClaims(JdRequirement requirement) {
        try {
            return claimRepository.list(null, null, null, null, 200).stream()
                    .filter(claim -> !"INVALID".equals(claim.validationStatus()))
                    .map(claim -> toClaimEvidence(requirement, claim))
                    .filter(match -> !match.matchedKeywords().isEmpty())
                    .sorted(Comparator.comparingDouble(MatchedEvidence::rrfScore).reversed())
                    .limit(3)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private MatchedEvidence toClaimEvidence(JdRequirement requirement, EvidenceClaim claim) {
        String haystack = String.join(" ",
                nullToBlank(claim.capability()),
                nullToBlank(claim.technology()),
                nullToBlank(claim.action()),
                nullToBlank(claim.projectName()),
                nullToBlank(claim.title()),
                nullToBlank(claim.headingPath()),
                nullToBlank(claim.quote())
        ).toLowerCase(Locale.ROOT);
        List<String> matched = matchedKeywords(requirement, haystack);
        return new MatchedEvidence(
                claim.sourceChunkId() == null ? claim.id() : claim.sourceChunkId(),
                claim.sourceType(),
                claim.title(),
                claim.sourceUri(),
                claim.headingPath(),
                claim.quote(),
                matched,
                1,
                null,
                null,
                claim.confidence()
        );
    }

    private MatchedEvidence toEvidence(JdRequirement requirement, RetrievedChunk chunk) {
        List<String> matchedKeywords = matchedKeywords(requirement, chunk);
        return new MatchedEvidence(
                chunk.id(),
                chunk.sourceType(),
                chunk.title(),
                chunk.sourceUri(),
                chunk.headingPath(),
                extractQuote(chunk.content(), matchedKeywords),
                matchedKeywords,
                chunk.finalRank(),
                chunk.vectorRank(),
                chunk.ftsRank(),
                chunk.rrfScore()
        );
    }

    private List<String> matchedKeywords(JdRequirement requirement, RetrievedChunk chunk) {
        String haystack = (chunk.title() + " " + chunk.headingPath() + " " + chunk.content()).toLowerCase(Locale.ROOT);
        return matchedKeywords(requirement, haystack);
    }

    private List<String> matchedKeywords(JdRequirement requirement, String haystack) {
        List<String> matched = new ArrayList<>();
        for (String keyword : requirement.keywordsOrEmpty()) {
            if (keyword != null && !keyword.isBlank() && haystack.contains(keyword.toLowerCase(Locale.ROOT))) {
                matched.add(keyword);
            }
        }
        return matched.stream().distinct().limit(6).toList();
    }

    private String extractQuote(String content, List<String> matchedKeywords) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").strip();
        int index = 0;
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String keyword : matchedKeywords) {
            int found = lower.indexOf(keyword.toLowerCase(Locale.ROOT));
            if (found >= 0) {
                index = found;
                break;
            }
        }
        int start = Math.max(0, index - 80);
        int end = Math.min(normalized.length(), index + 180);
        return normalized.substring(start, end).strip();
    }

    private double confidence(JdRequirement requirement, List<MatchedEvidence> evidence) {
        if (evidence.isEmpty()) {
            return 0.0;
        }
        int maxKeywords = evidence.stream()
                .mapToInt(item -> item.matchedKeywords().size())
                .max()
                .orElse(0);
        double score = 0.28 + (Math.min(evidence.size(), 3) * 0.16) + (Math.min(maxKeywords, 4) * 0.09);
        if ("HIGH".equals(requirement.riskLevel())) {
            score -= 0.12;
        }
        return Math.max(0.0, Math.min(0.95, Math.round(score * 100.0) / 100.0));
    }

    private String supportStatus(JdRequirement requirement, List<MatchedEvidence> evidence, double confidence) {
        if (evidence.size() >= 2 && confidence >= 0.58) {
            if ("HIGH".equals(requirement.riskLevel()) && confidence < 0.72) {
                return "WEAKLY_SUPPORTED";
            }
            return "SUPPORTED";
        }
        if (!evidence.isEmpty()) {
            return "WEAKLY_SUPPORTED";
        }
        if ("MUST_HAVE".equals(requirement.priority()) || isHardSkill(requirement.category())) {
            return "GAP_PROJECT_REQUIRED";
        }
        return "UNSUPPORTED";
    }

    private boolean isHardSkill(String category) {
        return List.of("技术栈", "架构能力", "工程交付", "经验年限").contains(category);
    }

    private String allowedResumeWording(JdRequirement requirement, String status) {
        return switch (status) {
            case "SUPPORTED" -> "可写：围绕“%s”，已有公开证据支撑，可表达为个人项目或技术资产中的真实实践。".formatted(requirement.description());
            case "WEAKLY_SUPPORTED" -> "弱表达：只能写“具备相关学习和项目实践基础”，不能写生产级、主导、大规模落地。";
            case "GAP_PROJECT_REQUIRED" -> "暂不写入简历。先补一个 3-7 天最小闭环项目，再根据产出的 README、代码和复盘改写。";
            default -> "不写入简历。当前知识库没有足够证据支撑。";
        };
    }

    private String forbiddenWording(JdRequirement requirement, String status) {
        if ("SUPPORTED".equals(status)) {
            return "不能把“%s”扩展成未被证据证明的生产规模、商业收入、正式工作职责、大厂经历或企业采用。".formatted(requirement.description());
        }
        if ("WEAKLY_SUPPORTED".equals(status)) {
            return "只能弱表达学习和项目实践基础，不能写主导、生产级、大规模落地或商业化成果。";
        }
        if ("GAP_PROJECT_REQUIRED".equals(status)) {
            return "缺口项目完成前不能进入简历正文；参考项目不能写成自己的经历。";
        }
        return "当前不写入简历。";
    }

    private String scoreExplanation(JdRequirement requirement, List<MatchedEvidence> evidence, double confidence) {
        if (evidence.isEmpty()) {
            return "未找到匹配 quote，confidence=0。";
        }
        int evidenceCount = evidence.size();
        int keywordCount = evidence.stream()
                .mapToInt(item -> item.matchedKeywords().size())
                .max()
                .orElse(0);
        String riskPenalty = "HIGH".equals(requirement.riskLevel()) ? "；高风险要求已扣分" : "";
        return "证据数=%d，最多命中关键词=%d，risk=%s%s，confidence=%.2f。".formatted(
                evidenceCount,
                keywordCount,
                requirement.riskLevel(),
                riskPenalty,
                confidence
        );
    }

    private String missingGap(JdRequirement requirement, String status) {
        if ("SUPPORTED".equals(status) || "WEAKLY_SUPPORTED".equals(status)) {
            return "";
        }
        return "缺少能证明“%s”的公开项目、README、博客复盘或运行记录。".formatted(requirement.description());
    }

    private String buildQuery(JdRequirement requirement) {
        String keywords = String.join(" ", requirement.keywordsOrEmpty());
        return (requirement.description() + " " + keywords).strip();
    }

    private int count(List<EvidenceMatrixRow> matrix, String status) {
        return (int) matrix.stream()
                .filter(row -> status.equals(row.supportStatus()))
                .count();
    }

    private String nullToBlank(String value) {
        return value == null ? "" : value;
    }
}
