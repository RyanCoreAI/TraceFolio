<script setup>
import { useTraceFolioWorkspace } from "../composables/useTraceFolioWorkspace";

defineProps({
  portfolio: {
    type: Object,
    required: true
  }
});

const { shorten } = useTraceFolioWorkspace();
</script>

<template>
  <article class="portfolio-preview">
    <div class="trace-meta">
      <span>{{ portfolio.format }}</span>
      <span>证据分 {{ portfolio.metadata.evidenceScore || 0 }}</span>
      <span>{{ portfolio.metadata.capabilityCount || 0 }} 个能力</span>
      <span>{{ portfolio.metadata.claimCount }} 条 claim</span>
      <span>{{ portfolio.metadata.matrixRows }} 条矩阵</span>
      <span>{{ portfolio.metadata.defenseCardCount || 0 }} 张防守卡</span>
      <span>{{ portfolio.metadata.gapProjectCount || 0 }} 个补项目</span>
      <span>{{ portfolio.metadata.privacyStatus || "PASS" }}</span>
    </div>
    <p v-if="portfolio.metadata.privacyIssues?.length" class="message">
      Privacy scan：{{ portfolio.metadata.privacyIssues.join("；") }}
    </p>
    <pre>{{ shorten(portfolio.content, 1600) }}</pre>
  </article>
</template>
