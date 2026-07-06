<script setup>
import { BookOpen, GitBranch, UploadCloud } from '@lucide/vue';
import EmptyState from '../components/EmptyState.vue';
import { useTraceFolioWorkspace } from '../composables/useTraceFolioWorkspace';

const { githubForm, blogForm, loading, ingestMessage, documents, ingestGitHub, ingestBlog, sourceTypeLabel, shorten } = useTraceFolioWorkspace();
</script>

<template>
<section id="sources" class="surface source-surface">
  <div class="section-heading">
    <div>
      <p class="section-label">资料管理</p>
      <h2>导入公开证据</h2>
    </div>
    <span class="section-hint">只导入公开博客、README 和文档</span>
  </div>

  <div class="source-layout">
    <form class="form source-form" @submit.prevent="ingestGitHub">
      <div class="form-title">
        <GitBranch :size="18" />
        GitHub README / 文档
      </div>
      <div class="form-row three">
        <label>
          <span>组织或用户</span>
          <input v-model.trim="githubForm.owner" autocomplete="off" />
        </label>
        <label>
          <span>仓库名</span>
          <input v-model.trim="githubForm.repo" autocomplete="off" />
        </label>
        <label>
          <span>文件路径</span>
          <input v-model.trim="githubForm.path" autocomplete="off" />
        </label>
      </div>
      <button class="button primary" type="submit" :disabled="loading.ingest">
        <UploadCloud :size="16" />
        导入 GitHub 资料
      </button>
    </form>

    <form class="form source-form" @submit.prevent="ingestBlog">
      <div class="form-title">
        <BookOpen :size="18" />
        公开博客文章
      </div>
      <label>
        <span>文章地址</span>
        <input v-model.trim="blogForm.url" autocomplete="off" />
      </label>
      <button class="button secondary" type="submit" :disabled="loading.ingest">
        <UploadCloud :size="16" />
        导入博客文章
      </button>
    </form>
  </div>

  <p class="message" :class="ingestMessage.tone">{{ ingestMessage.text }}</p>

  <div class="table-shell">
    <table>
      <thead>
        <tr>
          <th>类型</th>
          <th>标题</th>
          <th>片段数</th>
        </tr>
      </thead>
      <tbody>
        <tr v-if="!documents.length">
          <td colspan="3"><EmptyState text="暂无数据" compact /></td>
        </tr>
        <tr v-for="doc in documents" :key="doc.documentId">
          <td>{{ sourceTypeLabel(doc.sourceType) }}</td>
          <td>
            <a class="source-link" :href="doc.sourceUri" target="_blank" rel="noreferrer">
              {{ shorten(doc.title, 80) }}
            </a>
          </td>
          <td>{{ doc.chunkCount }}</td>
        </tr>
      </tbody>
    </table>
  </div>
</section>
</template>
