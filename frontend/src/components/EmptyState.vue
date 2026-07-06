<script setup>
defineProps({
  text: {
    type: String,
    required: true
  },
  compact: {
    type: Boolean,
    default: false
  }
});
</script>

<template>
  <p class="empty-note" :class="{ compact }">{{ text }}</p>
</template>
