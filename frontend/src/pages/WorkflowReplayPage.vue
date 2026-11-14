<script setup>
import { ClipboardCheck, GitBranch, Play, Route } from '@lucide/vue';
import ErrorNotice from '../components/ErrorNotice.vue';
import PortfolioPreview from '../components/PortfolioPreview.vue';
import WorkflowTimeline from '../components/WorkflowTimeline.vue';
import EmptyState from '../components/EmptyState.vue';
import { useTraceFolioWorkspace } from '../composables/useTraceFolioWorkspace';

const { workflow, productLoop, portfolio, repoForm, loading, currentJdRunId, runProductLoop, runJdWorkflow, runRepoWorkflow, recordWorkflowDecision, resumeWorkflow, retryWorkflowStep, exportPortfolio } = useTraceFolioWorkspace();
</script>

<template>
<section id="workflow" class="surface workflow-surface">
  <div class="section-heading">
    <div>
      <p class="section-label">Auditable Workflow</p>
      <h2>一键运行 JD 证据工作流，并导出 Portfolio Evidence Pack</h2>
    </div>
    <span class="section-hint">每一步输入、输出和失败原因都会落库回放</span>
  </div>

  <section class="product-loop-panel">
    <div>
      <p class="section-label">Product Loop</p>
      <h3>完整产品闭环</h3>
      <p>使用当前仓库扫描配置和 JD，一次性完成 repo scan、claim extract、JD match、resume compile、gap plan、Evidence Pack export、audit 和 product readiness。</p>
    </div>
    <div class="product-loop-actions">
      <button
        class="button primary"
        type="button"
        :disabled="loading.productLoop || (repoForm.source === 'LOCAL' && !repoForm.confirmOwnership)"
        @click="runProductLoop"
      >
        <Route :size="16" />
        运行完整产品闭环
      </button>
      <span v-if="repoForm.source === 'LOCAL' && !repoForm.confirmOwnership" class="muted">本地扫描需要先勾选仓库所有权确认。</span>
    </div>
  </section>

  <ErrorNotice v-if="productLoop.error" :message="productLoop.error" />
  <section v-if="productLoop.data" class="product-loop-result">
    <div class="trace-meta">
      <span>Run {{ productLoop.data.runId }}</span>
      <span>{{ productLoop.data.status }}</span>
      <span>JD {{ productLoop.data.output?.jdRunId }}</span>
      <span>{{ productLoop.data.output?.auditStatus }}</span>
      <span>{{ productLoop.data.output?.productStage }}</span>
    </div>
    <div class="summary-grid compact">
      <div class="summary-item">
        <span>Repo Claim</span>
        <strong>{{ productLoop.data.output?.insertedRepoClaims || 0 }}</strong>
      </div>
      <div class="summary-item">
        <span>Chunk Claim</span>
        <strong>{{ productLoop.data.output?.insertedChunkClaims || 0 }}</strong>
      </div>
      <div class="summary-item">
        <span>Evidence Pack</span>
        <strong>{{ productLoop.data.output?.portfolioExport?.metadata?.claimCount || 0 }}</strong>
      </div>
    </div>
    <div class="product-loop-links">
      <a
        v-if="productLoop.data.output?.portfolioExport?.contentUrl"
        class="button secondary"
        :href="productLoop.data.output.portfolioExport.contentUrl"
        target="_blank"
        rel="noreferrer"
      >
        打开 Evidence Pack
      </a>
      <a
        v-if="productLoop.data.output?.portfolioExport?.jsonUrl"
        class="button ghost"
        :href="productLoop.data.output.portfolioExport.jsonUrl"
        target="_blank"
        rel="noreferrer"
      >
        查看导出 JSON
      </a>
    </div>
    <WorkflowTimeline :steps="productLoop.data.steps" prefix="product" />
  </section>

  <div class="workflow-grid">
    <section class="panel-section">
      <div class="block-heading">
        <h3>JD 证据工作流</h3>
        <span v-if="workflow.data">{{ workflow.data.status }}</span>
      </div>
      <p class="muted">链路：claim extract -> JD compile -> evidence match -> resume compile -> gap plan。</p>
      <button class="button primary" type="button" :disabled="loading.workflow" @click="runJdWorkflow">
        <Route :size="16" />
        运行可回放工作流
      </button>
      <button class="button secondary" type="button" :disabled="loading.workflow" @click="runRepoWorkflow">
        <GitBranch :size="16" />
        运行 Repo Evidence Workflow
      </button>
      <ErrorNotice v-if="workflow.error" :message="workflow.error" />
      <div v-if="workflow.repoData" class="workflow-steps">
              <div class="trace-meta">
                <span>Repo Run {{ workflow.repoData.runId }}</span>
                <span>{{ workflow.repoData.status }}</span>
                <span>{{ workflow.repoData.output?.insertedClaims || 0 }} 条 claim</span>
              </div>
              <WorkflowTimeline :steps="workflow.repoData.steps" prefix="repo" />
            </div>
            <div v-if="workflow.data" class="workflow-steps">
        <div class="trace-meta">
          <span>Run {{ workflow.data.runId }}</span>
          <span>{{ workflow.data.currentStep }}</span>
          <span v-if="workflow.data.output?.jdRunId">JD {{ workflow.data.output.jdRunId }}</span>
          <span v-if="workflow.data.output?.humanReviewRequired">需要人工复核</span>
        </div>
        <div v-if="workflow.data.output?.validation" class="validation-box" :class="workflow.data.output.validation.passed ? 'good' : 'bad'">
          <strong>简历 claim 校验：{{ workflow.data.output.validation.passed ? "通过" : "未通过" }}</strong>
          <p v-for="item in workflow.data.output.validation.errors" :key="item">{{ item }}</p>
          <p v-for="item in workflow.data.output.validation.warnings" :key="item">{{ item }}</p>
        </div>
        <WorkflowTimeline :steps="workflow.data.steps" prefix="jd" retryable @retry-step="retryWorkflowStep" />

        <form class="decision-form" @submit.prevent="recordWorkflowDecision">
          <div class="form-row three">
            <label>
              <span>步骤</span>
              <select v-model="workflow.decisionStep">
                <option value="resume_compile">resume_compile</option>
                <option value="validate_resume_claims">validate_resume_claims</option>
                <option value="gap_plan">gap_plan</option>
                <option value="portfolio_export">portfolio_export</option>
              </select>
            </label>
            <label>
              <span>决策</span>
              <select v-model="workflow.decision">
                <option value="APPROVED">APPROVED</option>
                <option value="REJECTED">REJECTED</option>
                <option value="NOTE">NOTE</option>
              </select>
            </label>
            <label>
              <span>理由</span>
              <input v-model.trim="workflow.decisionReason" placeholder="人工确认说明" />
            </label>
          </div>
          <button class="button ghost" type="submit" :disabled="loading.workflow">
            <ClipboardCheck :size="16" />
            记录人工决策
          </button>
          <button class="button secondary" type="button" :disabled="loading.workflow || !['WAITING_REVIEW', 'FAILED_VALIDATION'].includes(workflow.data.status)" @click="resumeWorkflow">
            <Play :size="16" />
            记录恢复
          </button>
        </form>

        <div v-if="workflow.data.decisions?.length" class="decision-list">
          <article v-for="decision in workflow.data.decisions" :key="decision.decisionId" class="decision-row">
            <strong>{{ decision.decision }} · {{ decision.stepName }}</strong>
            <p>{{ decision.reason || "无备注" }}</p>
          </article>
        </div>
      </div>
    </section>

    <section class="panel-section">
      <div class="block-heading">
        <h3>Portfolio Evidence Pack</h3>
        <span v-if="currentJdRunId">绑定 JD Run</span>
      </div>
      <div class="form portfolio-form">
        <label>
          <span>导出格式</span>
          <select v-model="portfolio.format">
            <option value="MARKDOWN">Markdown</option>
            <option value="HTML">HTML</option>
          </select>
        </label>
        <button class="button secondary" type="button" :disabled="loading.portfolio" @click="exportPortfolio">
          <ClipboardCheck :size="16" />
          生成 Evidence Pack
        </button>
      </div>
      <ErrorNotice v-if="portfolio.error" :message="portfolio.error" />
      <PortfolioPreview v-if="portfolio.data" :portfolio="portfolio.data" />
      <EmptyState v-else text="先抽取 EvidenceClaim，再导出可分享的证据包。" compact />
    </section>
  </div>
</section>
</template>
