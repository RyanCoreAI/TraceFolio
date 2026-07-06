import { computed, reactive, ref } from "vue";

let workspace;

export function useTraceFolioWorkspace() {
  if (workspace) {
    return workspace;
  }

  const navItems = [
    { id: "overview", label: "概览" },
    { id: "roadmap", label: "路线" },
    { id: "sources", label: "资料" },
    { id: "claims", label: "证据" },
    { id: "jd", label: "JD 编译器" },
    { id: "workflow", label: "工作流" },
    { id: "qa", label: "问答" },
    { id: "trace", label: "回放" }
  ];

  const status = ref(null);
  const audit = ref(null);
  const roadmap = ref(null);
  const documents = ref([]);
  const claims = ref([]);
  const ingestMessage = reactive({ text: "", tone: "" });
  const claimMessage = reactive({ text: "", tone: "" });
  const loading = reactive({
    status: false,
    audit: false,
    roadmap: false,
    documents: false,
    ingest: false,
    claims: false,
    repo: false,
    jd: false,
    workflow: false,
    productLoop: false,
    portfolio: false,
    qa: false,
    trace: false,
    eval: false
  });

  const githubForm = reactive({
    owner: "RyanCoreAI",
    repo: "patchbrake",
    path: "README.md"
  });

  const blogForm = reactive({
    url: "https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now/"
  });

  const repoForm = reactive({
    source: "GITHUB",
    owner: "RyanCoreAI",
    repo: "TraceFolio",
    branch: "",
    localPath: "D:\\TraceFolio",
    confirmOwnership: false,
    maxFiles: 28,
    scanMode: "FAST",
    includePaths: "",
    excludePaths: "target,node_modules,dist",
    commitSha: "",
    maxFileChars: 120000
  });

  const jdForm = reactive({
    jdText: "我们正在招聘 Java / Spring Boot 后端开发，要求熟悉 RAG、Spring AI、PGVector 或向量检索，了解 PostgreSQL、Docker、JWT、Redis 限流和 CI/CD。需要能独立完成接口设计、部署、监控和问题复盘，有 Agent、Tool Calling、可观测性实践经验优先。请提供可验证的项目经历和技术复盘。"
  });

  const jd = reactive({
    phase: "未运行",
    tone: "neutral",
    runId: "",
    compiled: null,
    matched: null,
    resume: null,
    gaps: null,
    error: ""
  });

  const repoScan = reactive({
    data: null,
    error: ""
  });

  const workflow = reactive({
    data: null,
    repoData: null,
    error: "",
    decisionStep: "resume_compile",
    decision: "APPROVED",
    decisionReason: ""
  });

  const productLoop = reactive({
    data: null,
    error: ""
  });

  const portfolio = reactive({
    format: "MARKDOWN",
    data: null,
    error: ""
  });

  const qaForm = reactive({
    question: "PatchBrake 是什么？"
  });

  const qa = reactive({
    status: "未提问",
    tone: "neutral",
    response: null,
    error: ""
  });

  const traceForm = reactive({
    traceId: ""
  });

  const trace = reactive({
    data: null,
    error: ""
  });

  const evalResult = reactive({
    suite: "core",
    tags: "",
    failFast: false,
    data: null,
    error: ""
  });

  const chunkMetric = computed(() => {
    const count = status.value?.knowledge?.chunks ?? 0;
    return `${count} 个片段`;
  });

  const hasJdResult = computed(() => Boolean(jd.compiled || jd.matched || jd.resume || jd.gaps || jd.error));

  const currentJdRunId = computed(() => productLoop.data?.output?.jdRunId || workflow.data?.output?.jdRunId || jd.runId || "");


  async function refreshAll() {
    await Promise.all([loadStatus(), loadAudit(), loadRoadmap(), loadDocuments(), loadClaims()]);
  }

  async function api(path, options = {}) {
    const response = await fetch(path, {
      ...options,
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json; charset=utf-8" } : {}),
        ...(options.headers || {})
      }
    });

    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const message = data?.message || data?.detail || `${response.status} ${response.statusText}`;
      const action = data?.action ? ` ${data.action}` : "";
      throw new Error(`${message}${action}`);
    }
    return data;
  }

  async function loadStatus() {
    loading.status = true;
    try {
      status.value = await api("/api/runtime/status");
    } finally {
      loading.status = false;
    }
  }

  async function loadDocuments() {
    loading.documents = true;
    try {
      documents.value = await api("/api/documents");
    } finally {
      loading.documents = false;
    }
  }

  async function loadAudit() {
    loading.audit = true;
    try {
      audit.value = await api("/api/audit/evidence-os");
    } catch {
      audit.value = null;
    } finally {
      loading.audit = false;
    }
  }

  async function loadRoadmap() {
    loading.roadmap = true;
    try {
      roadmap.value = await api("/api/product/roadmap");
    } catch {
      roadmap.value = null;
    } finally {
      loading.roadmap = false;
    }
  }

  async function loadClaims() {
    loading.claims = true;
    try {
      claims.value = await api("/api/claims?limit=24");
    } catch {
      claims.value = [];
    } finally {
      loading.claims = false;
    }
  }

  async function ingestGitHub() {
    await ingest("/api/ingest/github", { ...githubForm }, "GitHub 资料已导入");
  }

  async function ingestBlog() {
    await ingest("/api/ingest/blog", { ...blogForm }, "博客文章已导入");
  }

  async function ingest(path, payload, okMessage) {
    loading.ingest = true;
    ingestMessage.text = "正在导入和生成 embedding...";
    ingestMessage.tone = "";
    try {
      const result = await api(path, { method: "POST", body: JSON.stringify(payload) });
      ingestMessage.text = `${okMessage}：${result.chunkCount} 个片段`;
      ingestMessage.tone = "ok";
      await refreshAll();
    } catch (error) {
      ingestMessage.text = error.message;
      ingestMessage.tone = "error";
    } finally {
      loading.ingest = false;
    }
  }

  async function extractClaims() {
    loading.claims = true;
    claimMessage.text = "正在从 chunk 抽取 EvidenceClaim...";
    claimMessage.tone = "";
    try {
      const result = await api("/api/claims/extract", {
        method: "POST",
        body: JSON.stringify({ clearExisting: false, limit: 500 })
      });
      claimMessage.text = `扫描 ${result.scannedChunks} 个片段，新增 ${result.insertedClaims} 条 claim，跳过 ${result.skippedDuplicates} 条重复。`;
      claimMessage.tone = "ok";
      await refreshAll();
    } catch (error) {
      claimMessage.text = error.message;
      claimMessage.tone = "error";
    } finally {
      loading.claims = false;
    }
  }

  async function scanRepository() {
    loading.repo = true;
    repoScan.error = "";
    repoScan.data = null;
    try {
      repoScan.data = await api("/api/repos/scan", {
        method: "POST",
        body: JSON.stringify(repoPayload())
      });
      await Promise.all([loadStatus(), loadClaims(), loadAudit()]);
    } catch (error) {
      repoScan.error = error.message;
    } finally {
      loading.repo = false;
    }
  }

  async function compileJdEvidence() {
    loading.jd = true;
    jd.phase = "拆解 JD";
    jd.tone = "neutral";
    jd.error = "";
    jd.compiled = null;
    jd.matched = null;
    jd.resume = null;
    jd.gaps = null;

    try {
      const compiled = await api("/api/jd/compile", {
        method: "POST",
        body: JSON.stringify({ jdText: jdForm.jdText })
      });
      jd.compiled = compiled;
      jd.runId = compiled.runId;

      jd.phase = "匹配证据";
      const matched = await api("/api/jd/match", {
        method: "POST",
        body: JSON.stringify({ runId: compiled.runId })
      });
      jd.matched = matched;

      jd.phase = "生成简历";
      jd.resume = await api("/api/resume/compile", {
        method: "POST",
        body: JSON.stringify({ runId: compiled.runId })
      });

      jd.phase = "规划缺口";
      try {
        jd.gaps = await api("/api/gaps/projects", {
          method: "POST",
          body: JSON.stringify({ runId: compiled.runId, targetDays: 5 })
        });
      } catch (error) {
        jd.gaps = { projects: [], error: error.message };
      }

      jd.phase = "已完成";
      jd.tone = "good";
    } catch (error) {
      jd.phase = "失败";
      jd.tone = "bad";
      jd.error = error.message;
    } finally {
      loading.jd = false;
    }
  }

  async function runJdWorkflow() {
    loading.workflow = true;
    workflow.error = "";
    workflow.data = null;
    try {
      workflow.data = await api("/api/workflows/jd-evidence/run", {
        method: "POST",
        body: JSON.stringify({ jdText: jdForm.jdText, targetDays: 5, extractClaims: true })
      });
      await refreshAll();
    } catch (error) {
      workflow.error = error.message;
    } finally {
      loading.workflow = false;
    }
  }

  async function runRepoWorkflow() {
    loading.workflow = true;
    workflow.error = "";
    workflow.repoData = null;
    try {
      workflow.repoData = await api("/api/workflows/repo-evidence/run", {
        method: "POST",
        body: JSON.stringify({
          repoScan: repoPayload()
        })
      });
      await refreshAll();
    } catch (error) {
      workflow.error = error.message;
    } finally {
      loading.workflow = false;
    }
  }

  async function runProductLoop() {
    loading.productLoop = true;
    productLoop.error = "";
    productLoop.data = null;
    try {
      productLoop.data = await api("/api/workflows/product-loop/run", {
        method: "POST",
        body: JSON.stringify({
          jdText: jdForm.jdText,
          repoScan: repoPayload(),
          runRepoScan: true,
          extractClaims: true,
          targetDays: 5,
          portfolioFormat: portfolio.format,
          maxClaims: 40
        })
      });
      const exportId = productLoop.data?.output?.portfolioExport?.exportId;
      if (exportId) {
        portfolio.data = await api(`/api/portfolio/exports/${encodeURIComponent(exportId)}`);
      }
      await refreshAll();
    } catch (error) {
      productLoop.error = error.message;
    } finally {
      loading.productLoop = false;
    }
  }

  async function recordWorkflowDecision() {
    if (!workflow.data?.runId) {
      workflow.error = "请先运行工作流，再记录人工决策。";
      return;
    }
    loading.workflow = true;
    workflow.error = "";
    try {
      await api(`/api/workflows/${encodeURIComponent(workflow.data.runId)}/decisions`, {
        method: "POST",
        body: JSON.stringify({
          stepName: workflow.decisionStep,
          decision: workflow.decision,
          reason: workflow.decisionReason,
          metadata: { surface: "vue-workbench" }
        })
      });
      workflow.data = await api(`/api/workflows/${encodeURIComponent(workflow.data.runId)}`);
      workflow.decisionReason = "";
      await loadAudit();
    } catch (error) {
      workflow.error = error.message;
    } finally {
      loading.workflow = false;
    }
  }

  async function resumeWorkflow() {
    if (!workflow.data?.runId) {
      workflow.error = "请先运行 JD 工作流，再恢复。";
      return;
    }
    loading.workflow = true;
    workflow.error = "";
    try {
      workflow.data = await api(`/api/workflows/${encodeURIComponent(workflow.data.runId)}/resume`, {
        method: "POST",
        body: JSON.stringify({
          reason: workflow.decisionReason || "人工确认后继续",
          metadata: { surface: "vue-workbench" }
        })
      });
      workflow.decisionReason = "";
      await loadAudit();
    } catch (error) {
      workflow.error = error.message;
    } finally {
      loading.workflow = false;
    }
  }

  async function retryWorkflowStep(stepIndex) {
    if (!workflow.data?.runId) {
      workflow.error = "请先运行 JD 工作流，再重试步骤。";
      return;
    }
    loading.workflow = true;
    workflow.error = "";
    try {
      workflow.data = await api(`/api/workflows/${encodeURIComponent(workflow.data.runId)}/steps/${stepIndex}/retry`, {
        method: "POST",
        body: JSON.stringify({
          reason: workflow.decisionReason || "人工请求重试",
          overrideInput: {}
        })
      });
    } catch (error) {
      workflow.error = error.message;
    } finally {
      loading.workflow = false;
    }
  }

  async function exportPortfolio() {
    loading.portfolio = true;
    portfolio.error = "";
    portfolio.data = null;
    try {
      portfolio.data = await api("/api/portfolio/export", {
        method: "POST",
        body: JSON.stringify({
          title: "TraceFolio Portfolio Evidence Pack",
          format: portfolio.format,
          jdRunId: currentJdRunId.value || null,
          maxClaims: 30
        })
      });
      await Promise.all([loadStatus(), loadAudit()]);
    } catch (error) {
      portfolio.error = error.message;
    } finally {
      loading.portfolio = false;
    }
  }

  async function askQuestion() {
    loading.qa = true;
    qa.status = "生成中";
    qa.tone = "neutral";
    qa.error = "";
    qa.response = null;
    try {
      const response = await api("/api/qa", {
        method: "POST",
        body: JSON.stringify({ question: qaForm.question })
      });
      qa.response = response;
      qa.status = statusLabel(response.status);
      qa.tone = response.status === "ANSWERED" ? "good" : response.status === "ABSTAINED" ? "warn" : "bad";
      traceForm.traceId = response.traceId;
      await loadTrace(response.traceId);
      await loadStatus();
    } catch (error) {
      qa.status = "失败";
      qa.tone = "bad";
      qa.error = error.message;
    } finally {
      loading.qa = false;
    }
  }

  async function loadTrace(traceId = traceForm.traceId) {
    if (!traceId) {
      return;
    }
    loading.trace = true;
    trace.error = "";
    try {
      trace.data = await api(`/api/traces/${encodeURIComponent(traceId)}`);
    } catch (error) {
      trace.error = error.message;
    } finally {
      loading.trace = false;
    }
  }

  async function runEval() {
    loading.eval = true;
    evalResult.error = "";
    try {
      evalResult.data = await api("/api/evals/run", {
        method: "POST",
        body: JSON.stringify({
          suite: evalResult.suite,
          tags: splitCsv(evalResult.tags),
          failFast: evalResult.failFast
        })
      });
      await loadStatus();
    } catch (error) {
      evalResult.error = error.message;
    } finally {
      loading.eval = false;
    }
  }

  function repoPayload() {
    return {
      source: repoForm.source,
      owner: repoForm.owner,
      repo: repoForm.repo,
      branch: repoForm.branch || null,
      localPath: repoForm.source === "LOCAL" ? repoForm.localPath : null,
      confirmOwnership: repoForm.source === "LOCAL" ? repoForm.confirmOwnership : null,
      maxFiles: Number(repoForm.maxFiles) || 28,
      scanMode: repoForm.scanMode,
      includePaths: splitCsv(repoForm.includePaths),
      excludePaths: splitCsv(repoForm.excludePaths),
      commitSha: repoForm.commitSha || null,
      maxFileChars: Number(repoForm.maxFileChars) || null
    };
  }

  function splitCsv(value) {
    return String(value || "")
      .split(",")
      .map((item) => item.trim())
      .filter(Boolean);
  }

  function statusCards() {
    if (!status.value) {
      return [
        { title: "运行状态", tone: "warn", main: "正在加载", detail: "" }
      ];
    }

    const db = status.value.database;
    const embedding = status.value.embedding;
    const chat = status.value.chat;
    const knowledge = status.value.knowledge;

    return [
      {
        title: "数据库",
        tone: db.connected && db.vectorExtensionEnabled ? "good" : "bad",
        main: db.connected ? `${db.ragTableCount} 张表 · pgvector ${db.vectorExtensionEnabled ? "已启用" : "未启用"}` : "数据库不可用",
        detail: db.error
      },
      {
        title: "向量模型",
        tone: embedding.ollamaReachable && embedding.modelInstalled ? "good" : "bad",
        main: embedding.ollamaReachable
          ? `${embedding.modelInstalled ? "已安装" : "未安装"} · ${embedding.actualDimensions || "?"}/${embedding.expectedDimensions}`
          : "Ollama 不可用",
        detail: embedding.error
      },
      {
        title: "问答模型",
        tone: chat.apiKeyConfigured ? "good" : "warn",
        main: chat.apiKeyConfigured ? chat.model : "缺少 DEEPSEEK_API_KEY",
        detail: chat.baseUrl
      },
      {
        title: "知识库",
        tone: knowledge.chunks > 0 ? "good" : "warn",
        main: `${knowledge.documents} 份资料 · ${knowledge.chunks} 个片段`,
        detail: `${knowledge.claims || 0} 条 claim · ${knowledge.repoScans || 0} 次扫描 · ${knowledge.workflows || 0} 个工作流 · ${knowledge.portfolioExports || 0} 次导出`
      }
    ];
  }

  function sourceTypeLabel(type) {
    return {
      BLOG: "博客文章",
      GITHUB_README: "GitHub README",
      GITHUB_DOC: "GitHub 文档",
      GITHUB_REPO_FILE: "仓库文件"
    }[type] || type || "未知来源";
  }

  function statusLabel(value) {
    return {
      ANSWERED: "已回答",
      ABSTAINED: "已拒答",
      FAILED_VALIDATION: "校验失败"
    }[value] || value || "未知状态";
  }

  function supportLabel(value) {
    return {
      SUPPORTED: "强证据",
      WEAKLY_SUPPORTED: "弱证据",
      UNSUPPORTED: "拒写",
      GAP_PROJECT_REQUIRED: "需补项目"
    }[value] || value || "未知";
  }

  function supportTone(value) {
    return {
      SUPPORTED: "good",
      WEAKLY_SUPPORTED: "warn",
      UNSUPPORTED: "bad",
      GAP_PROJECT_REQUIRED: "info"
    }[value] || "neutral";
  }

  function claimStrengthTone(value) {
    return {
      STRONG: "good",
      MEDIUM: "warn",
      WEAK: "info"
    }[value] || "neutral";
  }

  function auditTone(value) {
    return {
      READY: "good",
      PARTIAL: "warn",
      BLOCKED: "bad",
      PASS: "good",
      WARN: "warn",
      FAIL: "bad"
    }[value] || "neutral";
  }

  function roadmapTone(value) {
    return {
      RUNTIME_VERIFIED: "good",
      CODE_COMPLETE_NEEDS_RUNTIME_DATA: "warn",
      IMPLEMENTED: "info",
      PRODUCT_BASELINE_READY: "good",
      FULL_ROADMAP_CODE_COMPLETE: "warn",
      IMPLEMENTATION_BASELINE: "info"
    }[value] || "neutral";
  }

  function shorten(value, limit = 90) {
    const text = String(value || "");
    return text.length > limit ? `${text.slice(0, limit - 1)}...` : text;
  }

  function formatNumber(value) {
    if (typeof value !== "number" || Number.isNaN(value)) {
      return "0";
    }
    return value.toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
  }

  workspace = {
    navItems,
    status,
    audit,
    roadmap,
    documents,
    claims,
    ingestMessage,
    claimMessage,
    loading,
    githubForm,
    blogForm,
    repoForm,
    jdForm,
    jd,
    repoScan,
    workflow,
    productLoop,
    portfolio,
    qaForm,
    qa,
    traceForm,
    trace,
    evalResult,
    chunkMetric,
    hasJdResult,
    currentJdRunId,
    refreshAll,
    api,
    loadStatus,
    loadDocuments,
    loadAudit,
    loadRoadmap,
    loadClaims,
    ingestGitHub,
    ingestBlog,
    ingest,
    extractClaims,
    scanRepository,
    compileJdEvidence,
    runJdWorkflow,
    runRepoWorkflow,
    runProductLoop,
    recordWorkflowDecision,
    resumeWorkflow,
    retryWorkflowStep,
    exportPortfolio,
    askQuestion,
    loadTrace,
    runEval,
    repoPayload,
    splitCsv,
    statusCards,
    sourceTypeLabel,
    statusLabel,
    supportLabel,
    supportTone,
    claimStrengthTone,
    auditTone,
    roadmapTone,
    shorten,
    formatNumber
  };

  return workspace;
}
