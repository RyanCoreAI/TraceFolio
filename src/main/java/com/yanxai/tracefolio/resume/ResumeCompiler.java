package com.yanxai.tracefolio.resume;

import com.yanxai.tracefolio.jd.EvidenceMatrixRow;
import com.yanxai.tracefolio.jd.MatchedEvidence;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ResumeCompiler {

    public ResumeCompileResponse compile(java.util.UUID runId, List<EvidenceMatrixRow> matrix) {
        List<EvidenceMatrixRow> rows = matrix == null ? List.of() : matrix;
        List<ResumeClaim> claims = supportedClaims(rows);
        List<String> refused = refusedClaims(rows);
        ResumeDraft draft = new ResumeDraft(
                skillSummary(rows),
                projectExperiences(claims),
                selfEvaluation(rows),
                interviewDefense(claims),
                "当前证据库未发现可证明正式工作经历的材料，本次不生成工作经历；只生成个人项目、开源项目、技术复盘和项目实践。"
        );
        return new ResumeCompileResponse(runId, draft, claims, refused);
    }

    private List<ResumeClaim> supportedClaims(List<EvidenceMatrixRow> rows) {
        return rows.stream()
                .filter(row -> "SUPPORTED".equals(row.supportStatus()) || "WEAKLY_SUPPORTED".equals(row.supportStatus()))
                .filter(row -> row.evidenceOrEmpty().stream().anyMatch(evidence -> evidence.quote() != null && !evidence.quote().isBlank()))
                .map(row -> new ResumeClaim(
                        row.requirementId(),
                        row.supportStatus(),
                        wording(row),
                        row.evidenceOrEmpty()
                ))
                .limit(8)
                .toList();
    }

    private List<String> refusedClaims(List<EvidenceMatrixRow> rows) {
        return rows.stream()
                .filter(row -> "UNSUPPORTED".equals(row.supportStatus()) || "GAP_PROJECT_REQUIRED".equals(row.supportStatus()))
                .map(row -> "%s：%s。原因：%s 禁止写法：%s".formatted(
                        row.requirementId(),
                        row.requirement(),
                        row.missingGap(),
                        row.forbiddenWording()
                ))
                .toList();
    }

    private List<String> skillSummary(List<EvidenceMatrixRow> rows) {
        List<String> skills = new ArrayList<>();
        for (EvidenceMatrixRow row : rows) {
            if ("SUPPORTED".equals(row.supportStatus())) {
                skills.add("可证明能力：" + row.requirement());
            } else if ("WEAKLY_SUPPORTED".equals(row.supportStatus())) {
                skills.add("弱证据能力：" + row.requirement() + "，建议用学习/实践基础表达。");
            }
            if (skills.size() >= 6) {
                break;
            }
        }
        if (skills.isEmpty()) {
            return List.of("当前证据不足，建议先补充 README、项目文档或技术复盘后再生成简历。");
        }
        return skills;
    }

    private List<ProjectExperience> projectExperiences(List<ResumeClaim> claims) {
        Map<String, List<String>> bulletsByProject = new LinkedHashMap<>();
        Map<String, String> sourceByProject = new LinkedHashMap<>();
        for (ResumeClaim claim : claims) {
            MatchedEvidence evidence = claim.evidence().isEmpty() ? null : claim.evidence().getFirst();
            String project = evidence == null ? "公开技术资产项目" : emptyToDefault(evidence.title(), "公开技术资产项目");
            sourceByProject.putIfAbsent(project, evidence == null ? "" : evidence.sourceUri());
            bulletsByProject.computeIfAbsent(project, ignored -> new ArrayList<>()).add(claim.wording());
        }

        return bulletsByProject.entrySet().stream()
                .map(entry -> new ProjectExperience(entry.getKey(), sourceByProject.get(entry.getKey()), entry.getValue()))
                .toList();
    }

    private List<String> selfEvaluation(List<EvidenceMatrixRow> rows) {
        long supported = rows.stream().filter(row -> "SUPPORTED".equals(row.supportStatus())).count();
        long weak = rows.stream().filter(row -> "WEAKLY_SUPPORTED".equals(row.supportStatus())).count();
        long gaps = rows.stream().filter(row -> "GAP_PROJECT_REQUIRED".equals(row.supportStatus())).count();
        return List.of(
                "偏证据驱动的工程实践者，简历表达优先绑定公开项目、README 和技术复盘。",
                "当前 JD 匹配中有 %d 项强证据、%d 项弱证据、%d 项需要补项目闭环。".formatted(supported, weak, gaps),
                "对无证据的大规模生产、正式工作经历、商业结果等 claim 保持拒写。"
        );
    }

    private List<InterviewDefense> interviewDefense(List<ResumeClaim> claims) {
        return claims.stream()
                .map(claim -> new InterviewDefense(
                        "你能具体解释这条简历表述吗？",
                        "可以围绕“%s”解释，并回到引用材料中的原文证据。".formatted(claim.wording()),
                        "不能扩展成生产级、大规模采用、正式工作职责或商业结果；弱证据只能弱表达。",
                        claim.evidence()
                ))
                .limit(6)
                .toList();
    }

    private String wording(EvidenceMatrixRow row) {
        String prefix = "WEAKLY_SUPPORTED".equals(row.supportStatus()) ? "了解并实践过" : "在个人项目中实践";
        String quote = row.evidenceOrEmpty().isEmpty() ? "" : row.evidenceOrEmpty().getFirst().quote();
        String evidenceTail = quote.isBlank() ? "" : "，证据片段：" + quote;
        return "%s“%s”相关能力%s".formatted(prefix, row.requirement(), evidenceTail);
    }

    private String emptyToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
