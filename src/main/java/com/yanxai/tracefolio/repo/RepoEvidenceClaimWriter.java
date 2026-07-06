package com.yanxai.tracefolio.repo;

import com.yanxai.tracefolio.claims.EvidenceClaimDraft;
import com.yanxai.tracefolio.claims.EvidenceClaimRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RepoEvidenceClaimWriter {

    private final EvidenceClaimRepository claimRepository;

    public RepoEvidenceClaimWriter(EvidenceClaimRepository claimRepository) {
        this.claimRepository = claimRepository;
    }

    public int insertClaims(
            UUID scanId,
            String owner,
            String repo,
            String branch,
            String sourceType,
            List<RepoEvidenceItem> evidence
    ) {
        int inserted = 0;
        for (RepoEvidenceItem item : evidence) {
            EvidenceClaimDraft claim = new EvidenceClaimDraft(
                    "REPO_SCAN",
                    null,
                    scanId,
                    item.filePath(),
                    "REPO_CAPABILITY",
                    item.capability(),
                    item.capability(),
                    "只读仓库扫描发现“%s”相关工程证据。".formatted(item.capability()),
                    item.technology(),
                    owner + "/" + repo,
                    "仓库文件 `%s` 可证明“%s”。".formatted(item.filePath(), item.capability()),
                    sourceType,
                    owner + "/" + repo + " " + item.filePath(),
                    item.sourceUri(),
                    item.filePath(),
                    item.quote(),
                    item.strength(),
                    item.riskLevel(),
                    confidence(item.strength()),
                    "RULE_BASED_REPO_SCAN_V2",
                    metadata(owner, repo, branch, sourceType, item)
            );
            if (claimRepository.insert(claim)) {
                inserted++;
            }
        }
        return inserted;
    }

    private double confidence(String strength) {
        return switch (strength) {
            case "STRONG" -> 0.88;
            case "MEDIUM" -> 0.72;
            default -> 0.52;
        };
    }

    private Map<String, Object> metadata(String owner, String repo, String branch, String sourceType, RepoEvidenceItem item) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("owner", owner);
        metadata.put("repo", repo);
        metadata.put("branch", branch);
        metadata.put("filePath", item.filePath());
        metadata.put("lineNumber", item.lineNumber() == null ? "" : item.lineNumber());
        metadata.put("signals", item.signals());
        metadata.put("fileHash", item.fileHash());
        metadata.put("symbolType", item.symbolType());
        metadata.put("symbolName", item.symbolName());
        metadata.put("sourceKind", item.sourceKind());
        metadata.put("repoSourceType", sourceType);
        return metadata;
    }
}
