<script setup>
import { ClipboardCheck } from '@lucide/vue';
import MatrixTable from '../components/MatrixTable.vue';
import EmptyState from '../components/EmptyState.vue';
import { useTraceFolioWorkspace } from '../composables/useTraceFolioWorkspace';

const { jd, jdForm, loading, hasJdResult, compileJdEvidence, shorten } = useTraceFolioWorkspace();
</script>

<template>
<section id="jd" class="surface jd-surface">
  <div class="section-heading">
    <div>
      <p class="section-label">JD Evidence Compiler</p>
      <h2>从岗位要求到证据矩阵、简历草稿和补项目计划</h2>
    </div>
    <span class="status-pill" :class="jd.tone">{{ jd.phase }}</span>
  </div>

  <div class="jd-layout">
    <form class="form jd-form" @submit.prevent="compileJdEvidence">
      <label>
        <span>岗位 JD</span>
        <textarea v-model.trim="jdForm.jdText" rows="9" />
      </label>
      <button class="button primary" type="submit" :disabled="loading.jd">
        <ClipboardCheck :size="16" />
        一键生成证据方案
      </button>
    </form>

    <aside class="run-panel">
      <strong>编译链路</strong>
      <ol>
        <li>拆成原子岗位要求</li>
        <li>检索个人技术资产证据</li>
        <li>标注强证据、弱证据、拒写和缺口</li>
        <li>生成简历草稿与面试防守边界</li>
        <li>给缺口规划最小补证据项目</li>
      </ol>
      <p v-if="jd.runId" class="run-id">{{ jd.runId }}</p>
    </aside>
  </div>

  <div v-if="hasJdResult" class="jd-results">
    <p v-if="jd.error" class="message error">{{ jd.error }}</p>

    <div v-if="jd.matched" class="summary-grid jd-summary">
      <div class="summary-item">
        <span>总要求</span>
        <strong>{{ jd.matched.summary.totalRequirements }}</strong>
      </div>
      <div class="summary-item">
        <span>强证据</span>
        <strong>{{ jd.matched.summary.supported }}</strong>
      </div>
      <div class="summary-item">
        <span>弱证据</span>
        <strong>{{ jd.matched.summary.weaklySupported }}</strong>
      </div>
      <div class="summary-item">
        <span>需补项目</span>
        <strong>{{ jd.matched.summary.gapProjectRequired }}</strong>
      </div>
    </div>

    <div class="jd-two-col">
      <section v-if="jd.compiled" class="panel-section">
        <div class="block-heading">
          <h3>JD 需求拆解</h3>
          <span>{{ jd.compiled.requirements.length }} 条</span>
        </div>
        <div class="requirement-list">
          <article v-for="item in jd.compiled.requirements.slice(0, 12)" :key="item.id" class="requirement-row">
            <strong>{{ item.id }} · {{ item.category }}</strong>
            <p>{{ item.description }}</p>
            <span>{{ item.priority }} · {{ item.riskLevel }} · {{ item.requirementType }}<template v-if="item.hardGate"> · hard gate</template></span>
          </article>
        </div>
      </section>

      <MatrixTable v-if="jd.matched" :matrix="jd.matched.matrix" />
          </div>

    <div class="jd-two-col">
      <section v-if="jd.resume" class="panel-section">
        <div class="block-heading">
          <h3>岗位定制简历草稿</h3>
          <span>{{ jd.resume.claims.length }} 条可写</span>
        </div>
        <div class="resume-section">
          <h4>技能摘要</h4>
          <ul>
            <li v-for="item in jd.resume.draft.skillSummary" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div class="resume-section">
          <h4>项目经历</h4>
          <article v-for="project in jd.resume.draft.projectExperiences" :key="project.projectName" class="project-card">
            <strong>{{ project.projectName }}</strong>
            <ul>
              <li v-for="bullet in project.bullets" :key="bullet">{{ bullet }}</li>
            </ul>
          </article>
          <p v-if="!jd.resume.draft.projectExperiences.length" class="muted">没有足够证据生成项目经历 bullet。</p>
        </div>
        <div class="resume-section">
          <h4>拒写项</h4>
          <ul v-if="jd.resume.refusedClaims.length">
            <li v-for="item in jd.resume.refusedClaims.slice(0, 8)" :key="item">{{ item }}</li>
          </ul>
          <p v-else class="muted">没有拒写项。</p>
        </div>
        <p class="muted">{{ jd.resume.draft.workExperienceNotice }}</p>
      </section>

      <section v-if="jd.gaps" class="panel-section">
        <div class="block-heading">
          <h3>缺口补证据项目</h3>
          <span>{{ jd.gaps.projects.length }} 个计划</span>
        </div>
        <p v-if="jd.gaps.error" class="message">{{ jd.gaps.error }}</p>
        <article v-for="project in jd.gaps.projects.slice(0, 4)" :key="project.requirementId" class="gap-card">
          <strong>{{ project.projectName }}</strong>
          <p>{{ project.objective }}</p>
          <p class="muted">搜索策略：{{ project.searchQuery }}</p>
          <ul>
            <li v-for="item in project.acceptanceCriteria" :key="item">{{ item }}</li>
          </ul>
          <div v-if="project.referenceProjects.length" class="reference-list">
            <a v-for="repo in project.referenceProjects" :key="repo.fullName" :href="repo.url" target="_blank" rel="noreferrer">
              {{ repo.fullName }} · {{ repo.stars || 0 }} stars
            </a>
          </div>
          <p v-if="project.scoutWarning" class="muted">{{ project.scoutWarning }}</p>
        </article>
        <p v-if="!jd.gaps.projects.length && !jd.gaps.error" class="muted">当前没有需要补项目的硬缺口。</p>
      </section>
    </div>
  </div>
  <EmptyState v-else text="导入项目 README 或博客后，粘贴目标 JD 即可开始。" />
</section>
</template>
