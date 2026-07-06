<script setup>
import EvidenceQuote from "./EvidenceQuote.vue";
import { useTraceFolioWorkspace } from "../composables/useTraceFolioWorkspace";

defineProps({
  matrix: {
    type: Array,
    default: () => []
  }
});

const { supportTone, supportLabel, shorten } = useTraceFolioWorkspace();
</script>

<template>
  <section class="panel-section">
    <div class="block-heading">
      <h3>能力证据矩阵</h3>
      <span>{{ matrix.length }} 条</span>
    </div>
    <div class="matrix-list">
      <article v-for="row in matrix.slice(0, 10)" :key="row.requirementId" class="matrix-row">
        <div class="matrix-head">
          <span class="status-pill" :class="supportTone(row.supportStatus)">
            {{ supportLabel(row.supportStatus) }}
          </span>
          <strong>{{ row.requirementId }} · {{ row.category }}</strong>
        </div>
        <p>{{ row.requirement }}</p>
        <p class="muted">{{ row.allowedResumeWording || row.missingGap }}</p>
        <p v-if="row.forbiddenWording" class="muted">禁止写法：{{ row.forbiddenWording }}</p>
        <p v-if="row.scoreExplanation" class="muted">评分解释：{{ row.scoreExplanation }}</p>
        <EvidenceQuote
          v-for="evidence in row.evidence.slice(0, 2)"
          :key="evidence.chunkId"
          :quote="evidence.quote"
          :source="shorten(evidence.title || evidence.sourceUri, 82)"
        />
        <p v-if="!row.evidence.length" class="muted">{{ row.missingGap || "未找到可引用证据。" }}</p>
      </article>
    </div>
  </section>
</template>
