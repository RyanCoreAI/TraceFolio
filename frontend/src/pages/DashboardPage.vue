<script setup>
import StatusCard from '../components/StatusCard.vue';
import EmptyState from '../components/EmptyState.vue';
import { useTraceFolioWorkspace } from '../composables/useTraceFolioWorkspace';

const { chunkMetric, audit, roadmap, statusCards, auditTone, roadmapTone, shorten } = useTraceFolioWorkspace();
</script>

<template>
<section id="overview" class="page-title">
  <div>
    <p class="eyebrow">可信 RAG 工作台</p>
    <h1>用公开证据回答技术资产问题，并把 JD 编译成可防守简历</h1>
  </div>
  <span class="metric">{{ chunkMetric }}</span>
</section>

<section class="surface overview-panel">
  <div class="section-heading">
    <div>
      <p class="section-label">运行概览</p>
      <h2>服务状态</h2>
    </div>
  </div>
  <div class="status-strip" aria-live="polite">
    <StatusCard v-for="card in statusCards()" :key="card.title" :card="card" />
  </div>

  <div class="audit-panel">
    <div class="audit-head">
      <div>
        <p class="section-label">Evidence OS Audit</p>
        <h3>证据操作系统验收</h3>
      </div>
      <span v-if="audit" class="status-pill" :class="auditTone(audit.status)">
        {{ audit.status }} · {{ audit.passedChecks }}/{{ audit.totalChecks }}
      </span>
      <span v-else class="status-pill neutral">等待后端</span>
    </div>
    <div v-if="audit" class="audit-list">
      <article v-for="check in audit.checks" :key="check.id" class="audit-check" :class="auditTone(check.status)">
        <span>{{ check.status }}</span>
        <strong>{{ check.title }}</strong>
        <p>{{ check.detail || check.action }}</p>
      </article>
    </div>
    <EmptyState v-else text="启动 Spring Boot 后，这里会显示 claim、repo scan、workflow 和 Portfolio 的验收结果。" />
  </div>
</section>

<section id="roadmap" class="surface roadmap-surface">
  <div class="section-heading">
    <div>
      <p class="section-label">Product Roadmap</p>
      <h2>完整产品路线和当前完成度</h2>
    </div>
    <span v-if="roadmap" class="status-pill" :class="roadmapTone(roadmap.currentStage)">
      {{ roadmap.currentStage }} · {{ roadmap.overallCompletion }}%
    </span>
    <span v-else class="status-pill neutral">等待后端</span>
  </div>

  <template v-if="roadmap">
    <p class="roadmap-positioning">{{ roadmap.positioning }}</p>
    <div class="summary-grid roadmap-summary">
      <div class="summary-item">
        <span>资料</span>
        <strong>{{ roadmap.runtimeEvidence.documents }}</strong>
      </div>
      <div class="summary-item">
        <span>Claim</span>
        <strong>{{ roadmap.runtimeEvidence.claims }}</strong>
      </div>
      <div class="summary-item">
        <span>工作流</span>
        <strong>{{ roadmap.runtimeEvidence.workflows }}</strong>
      </div>
      <div class="summary-item">
        <span>导出包</span>
        <strong>{{ roadmap.runtimeEvidence.portfolioExports }}</strong>
      </div>
    </div>

    <div class="roadmap-layout">
      <section class="roadmap-main">
        <article v-for="phase in roadmap.phases" :key="phase.id" class="roadmap-phase">
          <div class="roadmap-phase-head">
            <div>
              <strong>{{ phase.id }} · {{ phase.title }}</strong>
              <p>{{ phase.productOutcome }}</p>
            </div>
            <span class="status-pill" :class="roadmapTone(phase.status)">
              {{ phase.status }}
            </span>
          </div>
          <div class="progress-track" aria-hidden="true">
            <span :style="{ width: `${phase.completion}%` }"></span>
          </div>
          <div class="phase-meta">
            <span>{{ phase.completion }}%</span>
            <span>{{ phase.apiContracts.length }} 个接口</span>
            <span>{{ phase.openRisks.length }} 个风险</span>
          </div>
          <div class="phase-columns">
            <div>
              <h4>已交付</h4>
              <ul>
                <li v-for="item in phase.delivered.slice(0, 4)" :key="item">{{ item }}</li>
              </ul>
            </div>
            <div>
              <h4>验收口径</h4>
              <ul>
                <li v-for="item in phase.acceptanceCriteria.slice(0, 3)" :key="item">{{ item }}</li>
              </ul>
            </div>
          </div>
          <p v-if="phase.openRisks.length" class="muted">剩余风险：{{ phase.openRisks.join("；") }}</p>
        </article>
      </section>

      <aside class="roadmap-side">
        <section class="panel-section">
          <div class="block-heading">
            <h3>差异化</h3>
            <span>{{ roadmap.differentiators.length }} 条</span>
          </div>
          <ul>
            <li v-for="item in roadmap.differentiators" :key="item">{{ item }}</li>
          </ul>
        </section>

        <section class="panel-section">
          <div class="block-heading">
            <h3>发布验收</h3>
            <span>{{ roadmap.releaseGate.length }} 项</span>
          </div>
          <ul>
            <li v-for="item in roadmap.releaseGate" :key="item">{{ item }}</li>
          </ul>
        </section>

        <section class="panel-section">
          <div class="block-heading">
            <h3>当前阻塞</h3>
            <span>{{ roadmap.currentBlockers.length }}</span>
          </div>
          <p v-if="!roadmap.currentBlockers.length" class="muted">当前没有产品路线阻塞项。</p>
          <ul v-else>
            <li v-for="item in roadmap.currentBlockers" :key="item">{{ item }}</li>
          </ul>
        </section>
      </aside>
    </div>
  </template>
  <p v-else class="empty-note">启动 Spring Boot 后，这里会显示 V1-V6 的完成度、接口、验收口径和剩余阻塞。</p>
</section>
</template>
