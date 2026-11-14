<script setup>
import { onMounted } from 'vue';
import { Database, RefreshCw } from '@lucide/vue';
import DashboardPage from './pages/DashboardPage.vue';
import SourcesPage from './pages/SourcesPage.vue';
import EvidenceClaimsPage from './pages/EvidenceClaimsPage.vue';
import JdCompilerPage from './pages/JdCompilerPage.vue';
import WorkflowReplayPage from './pages/WorkflowReplayPage.vue';
import QaTracePage from './pages/QaTracePage.vue';
import { useTraceFolioWorkspace } from './composables/useTraceFolioWorkspace';

const { navItems, loading, refreshAll } = useTraceFolioWorkspace();

onMounted(() => {
  refreshAll();
});
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <a class="brand" href="#overview" aria-label="TraceFolio 首页">
        <span class="brand-mark">TF</span>
        <span>
          <strong>TraceFolio</strong>
          <small>个人技术资产证据库</small>
        </span>
      </a>

      <nav class="top-nav" aria-label="主导航">
        <a v-for="item in navItems" :key="item.id" :href="`#${item.id}`">{{ item.label }}</a>
      </nav>

      <div class="header-actions">
        <button class="button secondary" type="button" :disabled="loading.status" @click="refreshAll">
          <RefreshCw :size="16" />
          刷新状态
        </button>
        <a class="button ghost" href="/api/runtime/status" target="_blank" rel="noreferrer">
          <Database :size="16" />
          状态 JSON
        </a>
      </div>
    </header>

    <main class="page">
      <DashboardPage />
      <SourcesPage />
      <EvidenceClaimsPage />
      <JdCompilerPage />
      <WorkflowReplayPage />
      <QaTracePage />
    </main>
  </div>
</template>
