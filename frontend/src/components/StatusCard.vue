<script setup>
defineProps({
  card: {
    type: Object,
    required: true
  }
});
</script>

<template>
  <article class="status-card" :class="card.tone">
    <strong>{{ card.title }}</strong>
    <span>{{ card.main }}</span>
    <span v-if="card.detail" :title="card.detail">{{ card.detail }}</span>
  </article>
</template>
