<script setup>
defineProps({
  quote: {
    type: String,
    default: ""
  },
  source: {
    type: String,
    default: ""
  }
});
</script>

<template>
  <blockquote class="evidence-quote">
    {{ quote }}
    <span v-if="source">{{ source }}</span>
  </blockquote>
</template>
