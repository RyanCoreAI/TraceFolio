const state = {
  lastTraceId: null
};

const els = {
  statusStrip: document.querySelector("#statusStrip"),
  chunkMetric: document.querySelector("#chunkMetric"),
  documentsBody: document.querySelector("#documentsBody"),
  ingestMessage: document.querySelector("#ingestMessage"),
  qaStatus: document.querySelector("#qaStatus"),
  answerBlock: document.querySelector("#answerBlock"),
  traceList: document.querySelector("#traceList"),
  evalSummary: document.querySelector("#evalSummary"),
  refreshStatusBtn: document.querySelector("#refreshStatusBtn"),
  githubForm: document.querySelector("#githubForm"),
  blogForm: document.querySelector("#blogForm"),
  qaForm: document.querySelector("#qaForm"),
  traceForm: document.querySelector("#traceForm"),
  runEvalBtn: document.querySelector("#runEvalBtn")
};

document.addEventListener("DOMContentLoaded", () => {
  bindEvents();
  refreshAll();
});

function bindEvents() {
  els.refreshStatusBtn.addEventListener("click", refreshAll);
  els.githubForm.addEventListener("submit", ingestGitHub);
  els.blogForm.addEventListener("submit", ingestBlog);
  els.qaForm.addEventListener("submit", askQuestion);
  els.traceForm.addEventListener("submit", loadTraceFromForm);
  els.runEvalBtn.addEventListener("click", runEval);
}

async function refreshAll() {
  await Promise.all([loadStatus(), loadDocuments()]);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      "Accept": "application/json",
      ...(options.body ? {"Content-Type": "application/json; charset=utf-8"} : {}),
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
  try {
    const status = await api("/api/runtime/status");
    renderStatus(status);
  } catch (error) {
    els.statusStrip.innerHTML = "";
    els.statusStrip.append(statusCard("运行状态", "bad", "状态接口不可用", error.message));
  }
}

async function loadDocuments() {
  try {
    const documents = await api("/api/documents");
    renderDocuments(documents);
  } catch (error) {
    els.documentsBody.innerHTML = `<tr><td colspan="3" class="muted">${escapeHtml(error.message)}</td></tr>`;
  }
}

async function ingestGitHub(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const payload = {
    owner: value(form, "owner"),
    repo: value(form, "repo"),
    path: value(form, "path") || "README.md"
  };
  await ingest("/api/ingest/github", payload, "GitHub 资料已导入");
}

async function ingestBlog(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  await ingest("/api/ingest/blog", {url: value(form, "url")}, "博客文章已导入");
}

async function ingest(path, payload, okMessage) {
  setMessage(els.ingestMessage, "处理中...", "");
  setIngestDisabled(true);
  try {
    const result = await api(path, {method: "POST", body: JSON.stringify(payload)});
    setMessage(
      els.ingestMessage,
      `${okMessage}：${result.chunkCount} 个片段`,
      "ok"
    );
    await refreshAll();
  } catch (error) {
    setMessage(els.ingestMessage, error.message, "error");
  } finally {
    setIngestDisabled(false);
  }
}

async function askQuestion(event) {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  const question = value(form, "question");
  setQaStatus("生成中", "neutral");
  els.answerBlock.innerHTML = `<p class="muted">正在检索证据...</p>`;
  try {
    const response = await api("/api/qa", {
      method: "POST",
      body: JSON.stringify({question})
    });
    state.lastTraceId = response.traceId;
    els.traceForm.elements.traceId.value = response.traceId;
    renderQa(response);
    await loadTrace(response.traceId);
    await loadStatus();
  } catch (error) {
    setQaStatus("失败", "bad");
    els.answerBlock.innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
  }
}

async function loadTraceFromForm(event) {
  event.preventDefault();
  const traceId = value(new FormData(event.currentTarget), "traceId");
  if (traceId) {
    await loadTrace(traceId);
  }
}

async function loadTrace(traceId) {
  els.traceList.innerHTML = `<p class="muted">正在加载 trace...</p>`;
  try {
    const trace = await api(`/api/traces/${encodeURIComponent(traceId)}`);
    renderTrace(trace);
  } catch (error) {
    els.traceList.innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
  }
}

async function runEval() {
  els.runEvalBtn.disabled = true;
  els.evalSummary.innerHTML = `<p class="muted">评测运行中...</p>`;
  try {
    const result = await api("/api/evals/run", {method: "POST"});
    renderEval(result);
    await loadStatus();
  } catch (error) {
    els.evalSummary.innerHTML = `<p class="muted">${escapeHtml(error.message)}</p>`;
  } finally {
    els.runEvalBtn.disabled = false;
  }
}

function renderStatus(status) {
  els.statusStrip.innerHTML = "";
  const dbTone = status.database.connected && status.database.vectorExtensionEnabled ? "good" : "bad";
  const dbText = status.database.connected
    ? `${status.database.ragTableCount} 张表 · pgvector ${status.database.vectorExtensionEnabled ? "已启用" : "未启用"}`
    : "数据库不可用";

  const embeddingTone = status.embedding.ollamaReachable && status.embedding.modelInstalled ? "good" : "bad";
  const embeddingText = status.embedding.ollamaReachable
    ? `${status.embedding.modelInstalled ? "已安装" : "未安装"} · ${status.embedding.actualDimensions || "?"}/${status.embedding.expectedDimensions}`
    : "Ollama 不可用";

  const chatTone = status.chat.apiKeyConfigured ? "good" : "warn";
  const chatText = status.chat.apiKeyConfigured ? status.chat.model : "缺少 DEEPSEEK_API_KEY";

  const knowledge = status.knowledge;
  const knowledgeTone = knowledge.chunks > 0 ? "good" : "warn";

  els.statusStrip.append(
    statusCard("数据库", dbTone, dbText, status.database.error),
    statusCard("向量模型", embeddingTone, embeddingText, status.embedding.error),
    statusCard("问答模型", chatTone, chatText, status.chat.baseUrl),
    statusCard("知识库", knowledgeTone, `${knowledge.documents} 份资料 · ${knowledge.chunks} 个片段`, `${knowledge.traces} 条回放 · ${knowledge.evalRuns} 次评测`)
  );
  els.chunkMetric.textContent = `${knowledge.chunks} 个片段`;
}

function statusCard(title, tone, main, detail) {
  const card = document.createElement("div");
  card.className = `status-card ${tone}`;
  card.innerHTML = `
    <strong>${escapeHtml(title)}</strong>
    <span>${escapeHtml(main || "")}</span>
    ${detail ? `<span title="${escapeAttr(detail)}">${escapeHtml(shorten(detail, 72))}</span>` : ""}
  `;
  return card;
}

function renderDocuments(documents) {
  if (!documents.length) {
    els.documentsBody.innerHTML = `<tr><td colspan="3" class="muted">暂无数据</td></tr>`;
    return;
  }

  els.documentsBody.innerHTML = documents.map((doc) => `
    <tr>
      <td>${escapeHtml(sourceTypeLabel(doc.sourceType))}</td>
      <td><a class="source-link" href="${escapeAttr(doc.sourceUri)}" target="_blank" rel="noreferrer">${escapeHtml(shorten(doc.title, 70))}</a></td>
      <td>${doc.chunkCount}</td>
    </tr>
  `).join("");
}

function renderQa(response) {
  const tone = response.status === "ANSWERED" ? "good" : response.status === "ABSTAINED" ? "warn" : "bad";
  setQaStatus(statusLabel(response.status), tone);
  const summary = response.retrievalSummary || {};
  const citations = response.citations || [];
  const unsupportedClaims = response.unsupportedClaims || [];

  els.answerBlock.innerHTML = `
    <div class="trace-meta">
      <span>回放 ${escapeHtml(response.traceId)}</span>
      ${response.abstentionReason ? `<span>${escapeHtml(response.abstentionReason)}</span>` : ""}
    </div>
    <h3>回答结果</h3>
    <p class="answer-text">${escapeHtml(response.answer || "")}</p>
    ${summaryBlock(summary, ["入选证据", "向量召回", "关键词召回", "最佳 RRF"])}
    ${citationsBlock(citations)}
    ${claimsBlock(unsupportedClaims)}
  `;
}

function renderTrace(trace) {
  const chunks = trace.retrievedChunks || [];
  if (!chunks.length) {
    els.traceList.innerHTML = `<p class="muted">没有召回 chunk。</p>`;
    return;
  }

  els.traceList.innerHTML = `
    <div class="trace-meta">
      <span>${escapeHtml(statusLabel(trace.status))}</span>
      <span>回放 ${escapeHtml(trace.traceId)}</span>
      ${trace.abstentionReason ? `<span>${escapeHtml(trace.abstentionReason)}</span>` : ""}
    </div>
    ${chunks.map((chunk) => `
      <article class="trace-item">
        <h3>#${chunk.finalRank} · ${escapeHtml(shorten(chunk.title, 72))}</h3>
        <div class="trace-meta">
          <span>RRF ${formatNumber(chunk.rrfScore)}</span>
          ${chunk.vectorRank ? `<span>向量 ${chunk.vectorRank}</span>` : ""}
          ${chunk.ftsRank ? `<span>关键词 ${chunk.ftsRank}</span>` : ""}
        </div>
        <p class="muted">${escapeHtml(chunk.headingPath || "")}</p>
        <p>${escapeHtml(shorten(chunk.content || "", 420))}</p>
      </article>
    `).join("")}
  `;
}

function renderEval(result) {
  const failures = result.failures || [];
  const passRate = result.totalCases
    ? `${Math.round((result.passedCases / result.totalCases) * 100)}%`
    : "0%";
  els.evalSummary.innerHTML = `
    <strong>评测运行 ${escapeHtml(result.runId)}</strong>
    ${summaryBlock({
      selectedChunks: result.totalCases,
      vectorHits: result.passedCases,
      ftsHits: result.failedCases,
      bestRrfScore: passRate
    }, ["总数", "通过", "失败", "通过率"])}
    ${failures.length ? `<ol class="failures">${failures.slice(0, 8).map((failure) => `<li>${escapeHtml(failure)}</li>`).join("")}</ol>` : ""}
  `;
}

function summaryBlock(summary, labels = ["入选证据", "向量召回", "关键词召回", "最佳 RRF"]) {
  const values = [
    summary.selectedChunks ?? summary.totalCases ?? 0,
    summary.vectorHits ?? summary.passedCases ?? 0,
    summary.ftsHits ?? summary.failedCases ?? 0,
    typeof summary.bestRrfScore === "number" ? formatNumber(summary.bestRrfScore) : summary.bestRrfScore ?? ""
  ];
  return `
    <div class="summary-grid">
      ${values.map((item, index) => `
        <div class="summary-item">
          <span>${escapeHtml(labels[index])}</span>
          <strong>${escapeHtml(String(item))}</strong>
        </div>
      `).join("")}
    </div>
  `;
}

function citationsBlock(citations) {
  if (!citations.length) {
    return "";
  }
  return `
    <div class="citation-list">
      ${citations.map((citation) => `
        <article class="citation">
          <h3>${escapeHtml(sourceTypeLabel(citation.sourceType))} · ${escapeHtml(shorten(citation.title, 72))}</h3>
          <p class="muted">${escapeHtml(citation.headingPath || "")}</p>
          <blockquote>${escapeHtml(citation.quote || "")}</blockquote>
        </article>
      `).join("")}
    </div>
  `;
}

function claimsBlock(claims) {
  if (!claims.length) {
    return "";
  }
  return `<div class="claim-list">${claims.map((claim) => `<p class="muted">${escapeHtml(claim)}</p>`).join("")}</div>`;
}

function setQaStatus(text, tone) {
  els.qaStatus.textContent = text;
  els.qaStatus.className = `status-pill ${tone}`;
}

function setIngestDisabled(disabled) {
  document.querySelectorAll("#githubForm button, #blogForm button").forEach((button) => {
    button.disabled = disabled;
  });
}

function setMessage(element, text, tone) {
  element.textContent = text;
  element.className = `message ${tone || ""}`;
}

function value(form, key) {
  return String(form.get(key) || "").trim();
}

function shorten(value, limit) {
  const text = String(value || "");
  return text.length > limit ? `${text.slice(0, limit - 1)}...` : text;
}

function formatNumber(value) {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "0";
  }
  return value.toFixed(4).replace(/0+$/, "").replace(/\.$/, "");
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttr(value) {
  return escapeHtml(value).replaceAll("`", "&#096;");
}

function sourceTypeLabel(type) {
  const labels = {
    BLOG: "博客文章",
    GITHUB_README: "GitHub README",
    GITHUB_DOC: "GitHub 文档"
  };
  return labels[type] || type || "未知来源";
}

function statusLabel(status) {
  const labels = {
    ANSWERED: "已回答",
    ABSTAINED: "已拒答",
    FAILED_VALIDATION: "校验失败"
  };
  return labels[status] || status || "未知状态";
}
