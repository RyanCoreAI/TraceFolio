<script setup>
import { Database, GitBranch } from '@lucide/vue';
import EvidenceQuote from '../components/EvidenceQuote.vue';
import EmptyState from '../components/EmptyState.vue';
import { useTraceFolioWorkspace } from '../composables/useTraceFolioWorkspace';

const { claims, claimMessage, loading, repoScan, repoForm, extractClaims, scanRepository, claimStrengthTone, shorten } = useTraceFolioWorkspace();
</script>

<template>
<section id="claims" class="surface os-surface">
  <div class="section-heading">
    <div>
      <p class="section-label">EvidenceClaim Layer</p>
      <h2>把 chunk 编译成可进入简历和面试防守的证据声明</h2>
    </div>
    <button class="button secondary" type="button" :disabled="loading.claims" @click="extractClaims">
      <Database :size="16" />
      抽取 EvidenceClaim
    </button>
  </div>

  <p class="message" :class="claimMessage.tone">{{ claimMessage.text }}</p>

  <div class="os-grid">
    <section class="panel-section">
      <div class="block-heading">
        <h3>结构化 Claim</h3>
        <span>{{ claims.length }} 条</span>
      </div>
      <div class="claim-list">
        <article v-for="claim in claims.slice(0, 8)" :key="claim.id" class="claim-row">
          <div class="matrix-head">
            <span class="status-pill" :class="claimStrengthTone(claim.strength)">
              {{ claim.strength }}
            </span>
            <span class="status-pill" :class="claim.validationStatus === 'VALID' ? 'good' : claim.validationStatus === 'NEEDS_REVIEW' ? 'warn' : 'bad'">
              {{ claim.validationStatus || "UNKNOWN" }}
            </span>
            <span class="status-pill neutral">{{ claim.freshness || "UNKNOWN" }}</span>
            <strong>{{ claim.capability }} · {{ claim.technology }}</strong>
          </div>
          <p>{{ claim.action }}</p>
          <p class="muted">{{ claim.sourceRefType }} · {{ claim.filePath || claim.headingPath || claim.sourceUri }}</p>
          <EvidenceQuote :quote="claim.quote" :source="shorten(claim.title || claim.sourceUri, 84)" />
        </article>
        <EmptyState v-if="!claims.length" text="导入资料后运行 EvidenceClaim 抽取。" compact />
      </div>
    </section>

    <section class="panel-section">
      <div class="block-heading">
        <h3>只读仓库证据扫描</h3>
        <span v-if="repoScan.data">{{ repoScan.data.scannedFiles }} 个文件</span>
      </div>
      <form class="form repo-form" @submit.prevent="scanRepository">
        <label>
          <span>扫描来源</span>
          <select v-model="repoForm.source">
            <option value="GITHUB">GitHub 公开仓库</option>
            <option value="LOCAL">本地只读仓库</option>
          </select>
        </label>
        <div class="form-row two">
          <label>
            <span>组织或用户</span>
            <input v-model.trim="repoForm.owner" autocomplete="off" />
          </label>
          <label>
            <span>仓库名</span>
            <input v-model.trim="repoForm.repo" autocomplete="off" />
          </label>
        </div>
        <label v-if="repoForm.source === 'LOCAL'">
          <span>本地仓库路径</span>
          <input v-model.trim="repoForm.localPath" autocomplete="off" />
        </label>
        <div class="form-row two">
          <label>
            <span>分支</span>
            <input v-model.trim="repoForm.branch" placeholder="默认分支" autocomplete="off" />
          </label>
          <label>
            <span>最多文件数</span>
            <input v-model.number="repoForm.maxFiles" type="number" min="1" max="60" />
          </label>
        </div>
        <div class="form-row two">
          <label>
            <span>扫描模式</span>
            <select v-model="repoForm.scanMode">
              <option value="FAST">FAST：优先 README / 配置 / 核心分层</option>
              <option value="DEEP">DEEP：扩大到更多代码和文档文件</option>
            </select>
          </label>
          <label>
            <span>固定 commit sha</span>
            <input v-model.trim="repoForm.commitSha" placeholder="可选，留空使用分支" autocomplete="off" />
          </label>
        </div>
        <div class="form-row two">
          <label>
            <span>包含路径</span>
            <input v-model.trim="repoForm.includePaths" placeholder="src/main,docs" autocomplete="off" />
          </label>
          <label>
            <span>排除路径</span>
            <input v-model.trim="repoForm.excludePaths" placeholder="target,node_modules,dist" autocomplete="off" />
          </label>
        </div>
        <label>
          <span>单文件最大字符数</span>
          <input v-model.number="repoForm.maxFileChars" type="number" min="10000" max="300000" />
        </label>
        <label v-if="repoForm.source === 'LOCAL'" class="checkbox-row">
          <input v-model="repoForm.confirmOwnership" type="checkbox" />
          <span>我确认这是我拥有或可公开引用的仓库，只做本地只读扫描</span>
        </label>
        <button class="button primary" type="submit" :disabled="loading.repo">
          <GitBranch :size="16" />
          扫描仓库
        </button>
      </form>

      <p v-if="repoScan.error" class="message error">{{ repoScan.error }}</p>
      <div v-if="repoScan.data" class="repo-result">
        <div class="summary-grid compact">
          <div class="summary-item">
            <span>扫描文件</span>
            <strong>{{ repoScan.data.scannedFiles }}</strong>
          </div>
          <div class="summary-item">
            <span>证据项</span>
            <strong>{{ repoScan.data.evidence.length }}</strong>
          </div>
          <div class="summary-item">
            <span>新增 Claim</span>
            <strong>{{ repoScan.data.insertedClaims }}</strong>
          </div>
          <div class="summary-item">
            <span>扫描模式</span>
            <strong>{{ repoScan.data.scanMode || "FAST" }}</strong>
          </div>
          <div class="summary-item">
            <span>快照文件</span>
            <strong>{{ repoScan.data.selectedFiles?.length || 0 }}</strong>
          </div>
          <div class="summary-item">
            <span>Commit</span>
            <strong>{{ shorten(repoScan.data.commitSha || "未固定", 14) }}</strong>
          </div>
        </div>
        <article v-for="item in repoScan.data.evidence.slice(0, 6)" :key="`${item.filePath}-${item.capability}`" class="repo-evidence">
          <strong>{{ item.capability }} · {{ item.technology }}</strong>
          <p>
            {{ item.filePath }}<span v-if="item.lineNumber">#L{{ item.lineNumber }}</span>
            <span v-if="item.symbolType"> · {{ item.symbolType }} / {{ item.symbolName }}</span>
          </p>
          <EvidenceQuote :quote="item.quote" />
          <p class="muted">sourceKind={{ item.sourceKind || "FILE" }} · hash={{ shorten(item.fileHash, 16) }}</p>
        </article>
        <p v-for="warning in repoScan.data.warnings" :key="warning" class="muted">{{ warning }}</p>
      </div>
    </section>
  </div>
</section>
</template>
