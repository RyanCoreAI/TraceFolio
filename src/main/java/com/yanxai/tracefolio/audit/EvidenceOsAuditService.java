package com.yanxai.tracefolio.audit;

import com.yanxai.tracefolio.config.TraceFolioProperties;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class EvidenceOsAuditService {

    private final TraceFolioProperties properties;
    private final JdbcTemplate jdbcTemplate;

    public EvidenceOsAuditService(TraceFolioProperties properties, JdbcTemplate jdbcTemplate) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    public EvidenceOsAuditResult audit() {
        Map<String, Object> metrics = metrics();
        List<EvidenceOsAuditCheck> checks = checks(metrics);
        int passed = (int) checks.stream().filter(check -> "PASS".equals(check.status())).count();
        return new EvidenceOsAuditResult(
                overallStatus(checks),
                checks.size(),
                passed,
                metrics,
                checks,
                OffsetDateTime.now()
        );
    }

    static String overallStatus(List<EvidenceOsAuditCheck> checks) {
        if (checks.stream().anyMatch(check -> "FAIL".equals(check.status()))) {
            return "BLOCKED";
        }
        if (checks.stream().anyMatch(check -> "WARN".equals(check.status()))) {
            return "PARTIAL";
        }
        return "READY";
    }

    private Map<String, Object> metrics() {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("documents", count("SELECT COUNT(*) FROM rag_document WHERE tenant_id = ?"));
        metrics.put("chunks", count("SELECT COUNT(*) FROM rag_chunk WHERE tenant_id = ?"));
        metrics.put("claims", count("SELECT COUNT(*) FROM rag_evidence_claim WHERE tenant_id = ?"));
        metrics.put("chunkClaims", count("SELECT COUNT(*) FROM rag_evidence_claim WHERE tenant_id = ? AND source_ref_type = 'CHUNK'"));
        metrics.put("repoScanClaims", count("SELECT COUNT(*) FROM rag_evidence_claim WHERE tenant_id = ? AND source_ref_type = 'REPO_SCAN'"));
        metrics.put("claimsWithoutQuote", count("""
                SELECT COUNT(*)
                FROM rag_evidence_claim
                WHERE tenant_id = ?
                  AND (quote IS NULL OR btrim(quote) = '')
                """));
        metrics.put("invalidClaimRefs", count("""
                SELECT COUNT(*)
                FROM rag_evidence_claim
                WHERE tenant_id = ?
                  AND NOT (
                    (source_ref_type = 'CHUNK' AND source_chunk_id IS NOT NULL)
                    OR
                    (source_ref_type = 'REPO_SCAN' AND repo_scan_id IS NOT NULL)
                  )
                """));
        metrics.put("repoScans", count("SELECT COUNT(*) FROM rag_repo_scan WHERE tenant_id = ?"));
        metrics.put("repoScansWithoutClaims", count("""
                SELECT COUNT(*)
                FROM rag_repo_scan s
                WHERE s.tenant_id = ?
                  AND NOT EXISTS (
                    SELECT 1 FROM rag_evidence_claim c
                    WHERE c.repo_scan_id = s.id
                  )
                """));
        metrics.put("workflows", count("SELECT COUNT(*) FROM rag_workflow_run WHERE tenant_id = ?"));
        metrics.put("workflowSteps", count("""
                SELECT COUNT(*)
                FROM rag_workflow_step st
                JOIN rag_workflow_run r ON r.id = st.run_id
                WHERE r.tenant_id = ?
                """));
        metrics.put("workflowsWithoutSteps", count("""
                SELECT COUNT(*)
                FROM rag_workflow_run r
                WHERE r.tenant_id = ?
                  AND NOT EXISTS (
                    SELECT 1 FROM rag_workflow_step st
                    WHERE st.run_id = r.id
                  )
                """));
        metrics.put("failedValidationWorkflows", count("""
                SELECT COUNT(*)
                FROM rag_workflow_run
                WHERE tenant_id = ?
                  AND status = 'FAILED_VALIDATION'
                """));
        metrics.put("workflowDecisions", count("""
                SELECT COUNT(*)
                FROM rag_workflow_decision d
                JOIN rag_workflow_run r ON r.id = d.run_id
                WHERE r.tenant_id = ?
                """));
        metrics.put("portfolioExports", count("SELECT COUNT(*) FROM rag_portfolio_export WHERE tenant_id = ?"));
        metrics.put("emptyPortfolioExports", count("""
                SELECT COUNT(*)
                FROM rag_portfolio_export
                WHERE tenant_id = ?
                  AND (content IS NULL OR btrim(content) = '')
                """));
        return metrics;
    }

    private List<EvidenceOsAuditCheck> checks(Map<String, Object> metrics) {
        List<EvidenceOsAuditCheck> checks = new ArrayList<>();
        int documents = intMetric(metrics, "documents");
        int chunks = intMetric(metrics, "chunks");
        int claims = intMetric(metrics, "claims");
        int repoScans = intMetric(metrics, "repoScans");
        int repoScanClaims = intMetric(metrics, "repoScanClaims");
        int workflows = intMetric(metrics, "workflows");
        int portfolioExports = intMetric(metrics, "portfolioExports");

        checks.add(documents > 0 && chunks > 0
                ? pass("knowledge", "知识库已有资料和 chunk", "%d 份资料，%d 个 chunk。".formatted(documents, chunks))
                : warn("knowledge", "知识库还没有可验证资料", "先导入 README、docs 或博客，再运行 claim 抽取。"));
        checks.add(claims > 0
                ? pass("claims_present", "EvidenceClaim 已生成", "当前共有 %d 条 claim。".formatted(claims))
                : warn("claims_present", "还没有 EvidenceClaim", "运行 POST /api/claims/extract 或 POST /api/repos/scan。"));
        checks.add(intMetric(metrics, "claimsWithoutQuote") == 0
                ? pass("claim_quotes", "所有 claim 都有 quote", "无空 quote claim。")
                : fail("claim_quotes", "存在没有 quote 的 claim", "无 quote 的 claim 不能进入简历。"));
        checks.add(intMetric(metrics, "invalidClaimRefs") == 0
                ? pass("claim_refs", "所有 claim 都有来源回链", "CHUNK/REPO_SCAN 来源约束正常。")
                : fail("claim_refs", "存在来源断链 claim", "检查 source_ref_type、source_chunk_id、repo_scan_id。"));
        checks.add(repoScans == 0
                ? warn("repo_scan", "还没有仓库扫描记录", "运行 POST /api/repos/scan 扫描公开仓库。")
                : repoScanClaims > 0
                    ? pass(
                            "repo_scan",
                            "仓库扫描已沉淀 claim",
                            "%d 次扫描，%d 条 repo claim，%d 次历史空扫描。".formatted(
                                    repoScans,
                                    repoScanClaims,
                                    intMetric(metrics, "repoScansWithoutClaims")
                            )
                    )
                    : fail("repo_scan", "仓库扫描没有沉淀 claim", "检查 repo scanner 到 EvidenceClaim 的写入链路。"));
        checks.add(workflows == 0
                ? warn("workflow", "还没有工作流回放", "运行 POST /api/workflows/jd-evidence/run。")
                : intMetric(metrics, "workflowsWithoutSteps") == 0
                    ? pass("workflow", "工作流 step 可回放", "%d 个工作流，%d 个 step。".formatted(workflows, intMetric(metrics, "workflowSteps")))
                    : fail("workflow", "存在没有 step 的工作流", "检查 workflow step 持久化。"));
        checks.add(portfolioExports == 0
                ? warn("portfolio", "还没有 Evidence Pack 导出", "运行 POST /api/portfolio/export。")
                : intMetric(metrics, "emptyPortfolioExports") == 0
                    ? pass("portfolio", "Portfolio 导出内容非空", "%d 次导出。".formatted(portfolioExports))
                    : fail("portfolio", "存在空 Portfolio 导出", "检查导出生成器。"));
        return checks;
    }

    private EvidenceOsAuditCheck pass(String id, String title, String detail) {
        return new EvidenceOsAuditCheck(id, title, "PASS", detail, "");
    }

    private EvidenceOsAuditCheck warn(String id, String title, String action) {
        return new EvidenceOsAuditCheck(id, title, "WARN", "", action);
    }

    private EvidenceOsAuditCheck fail(String id, String title, String action) {
        return new EvidenceOsAuditCheck(id, title, "FAIL", "", action);
    }

    private int count(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class, properties.tenantId());
        return value == null ? 0 : value;
    }

    private int intMetric(Map<String, Object> metrics, String key) {
        Object value = metrics.get(key);
        return value instanceof Number number ? number.intValue() : 0;
    }
}
