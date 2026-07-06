<script setup>
defineProps({
  message: {
    type: String,
    default: ""
  }
});
</script>

<template>
  <p v-if="message" class="message error">{{ message }}</p>
</template>
