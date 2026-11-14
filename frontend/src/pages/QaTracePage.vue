<script setup>
import { Play, Route, Send } from '@lucide/vue';
import EvidenceQuote from '../components/EvidenceQuote.vue';
import EmptyState from '../components/EmptyState.vue';
import { useTraceFolioWorkspace } from '../composables/useTraceFolioWorkspace';

const { qa, qaForm, trace, traceForm, evalResult, loading, askQuestion, loadTrace, runEval, sourceTypeLabel, statusLabel, shorten, formatNumber } = useTraceFolioWorkspace();
</script>

<template>
<section class="work-grid">
  <section id="qa" class="surface qa-surface">
    <div class="section-heading">
      <div>
        <p class="section-label">证据问答</p>
        <h2>提出问题</h2>
      </div>
      <span class="status-pill" :class="qa.tone">{{ qa.status }}</span>
    </div>

    <form class="form ask-form" @submit.prevent="askQuestion">
      <label>
        <span>问题</span>
        <textarea v-model.trim="qaForm.question" rows="5" />
      </label>
      <button class="button primary" type="submit" :disabled="loading.qa">
        <Send :size="16" />
        提交问题
      </button>
    </form>

    <article class="answer-block">
      <p v-if="qa.error" class="muted">{{ qa.error }}</p>
      <template v-else-if="qa.response">
        <div class="trace-meta">
          <span>回放 {{ qa.response.traceId }}</span>
          <span v-if="qa.response.abstentionReason">{{ qa.response.abstentionReason }}</span>
        </div>
        <h3>回答结果</h3>
        <p class="answer-text">{{ qa.response.answer }}</p>
        <div class="summary-grid">
          <div class="summary-item"><span>入选证据</span><strong>{{ qa.response.retrievalSummary.selectedChunks }}</strong></div>
          <div class="summary-item"><span>向量召回</span><strong>{{ qa.response.retrievalSummary.vectorHits }}</strong></div>
          <div class="summary-item"><span>关键词召回</span><strong>{{ qa.response.retrievalSummary.ftsHits }}</strong></div>
          <div class="summary-item"><span>最佳 RRF</span><strong>{{ formatNumber(qa.response.retrievalSummary.bestRrfScore) }}</strong></div>
        </div>
        <p v-if="qa.response.retrievalSummary.queryExpansions?.length" class="muted">
          查询扩展：{{ qa.response.retrievalSummary.queryExpansions.join("、") }}
        </p>
        <article v-for="citation in qa.response.citations" :key="citation.chunkId" class="citation">
          <h3>{{ sourceTypeLabel(citation.sourceType) }} · {{ shorten(citation.title, 72) }}</h3>
          <p class="muted">{{ citation.headingPath }}</p>
          <EvidenceQuote :quote="citation.quote" />
        </article>
      </template>
      <EmptyState v-else text="回答和引用会显示在这里。" compact />
    </article>
  </section>

  <section id="trace" class="surface trace-surface">
    <div class="section-heading">
      <div>
        <p class="section-label">检索回放</p>
        <h2>Trace 与评测</h2>
      </div>
      <button class="button secondary" type="button" :disabled="loading.eval" @click="runEval">
        <Play :size="16" />
        运行评测
      </button>
    </div>

    <form class="trace-form" @submit.prevent="loadTrace()">
      <input v-model.trim="traceForm.traceId" placeholder="输入 traceId" />
      <button class="button ghost" type="submit" :disabled="loading.trace">
        <Route :size="16" />
        加载回放
      </button>
    </form>
    <form class="trace-form eval-form" @submit.prevent="runEval">
      <select v-model="evalResult.suite">
        <option value="core">core</option>
        <option value="all">all</option>
        <option value="jd">jd</option>
        <option value="repo">repo</option>
        <option value="workflow">workflow</option>
        <option value="portfolio">portfolio</option>
      </select>
      <input v-model.trim="evalResult.tags" placeholder="可选 tags，逗号分隔" />
      <label class="checkbox-row compact-checkbox">
        <input v-model="evalResult.failFast" type="checkbox" />
        <span>fail fast</span>
      </label>
      <button class="button secondary" type="submit" :disabled="loading.eval">
        <Play :size="16" />
        运行评测
      </button>
    </form>

    <div v-if="evalResult.error" class="eval-summary">{{ evalResult.error }}</div>
    <div v-if="evalResult.data" class="eval-summary">
      <strong>评测运行 {{ evalResult.data.runId }}</strong>
      <div class="summary-grid">
        <div class="summary-item"><span>套件</span><strong>{{ evalResult.data.suite }}</strong></div>
        <div class="summary-item"><span>总数</span><strong>{{ evalResult.data.totalCases }}</strong></div>
        <div class="summary-item"><span>通过</span><strong>{{ evalResult.data.passedCases }}</strong></div>
        <div class="summary-item"><span>失败</span><strong>{{ evalResult.data.failedCases }}</strong></div>
        <div class="summary-item"><span>通过率</span><strong>{{ evalResult.data.passRate || Math.round((evalResult.data.passedCases / evalResult.data.totalCases) * 100) }}%</strong></div>
      </div>
    </div>

    <div class="trace-list">
      <p v-if="trace.error" class="muted">{{ trace.error }}</p>
      <template v-else-if="trace.data">
        <div class="trace-meta">
          <span>{{ statusLabel(trace.data.status) }}</span>
          <span>回放 {{ trace.data.traceId }}</span>
          <span v-if="trace.data.abstentionReason">{{ trace.data.abstentionReason }}</span>
        </div>
        <article v-for="chunk in trace.data.retrievedChunks" :key="chunk.chunkId" class="trace-item">
          <h3>#{{ chunk.finalRank }} · {{ shorten(chunk.title, 72) }}</h3>
          <div class="trace-meta">
            <span>RRF {{ formatNumber(chunk.rrfScore) }}</span>
            <span v-if="chunk.vectorRank">向量 {{ chunk.vectorRank }}</span>
            <span v-if="chunk.ftsRank">关键词 {{ chunk.ftsRank }}</span>
          </div>
          <p class="muted">{{ chunk.headingPath }}</p>
          <p>{{ shorten(chunk.content, 420) }}</p>
        </article>
      </template>
      <EmptyState v-else text="检索证据、排名和 RRF 分数会显示在这里。" compact />
    </div>
  </section>
</section>
</template>
