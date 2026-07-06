<script setup>
defineProps({
  steps: {
    type: Array,
    default: () => []
  },
  prefix: {
    type: String,
    default: "workflow"
  },
  retryable: {
    type: Boolean,
    default: false
  }
});

defineEmits(["retry-step"]);

function tone(status) {
  if (status === "COMPLETED") {
    return "good";
  }
  if (status === "SKIPPED") {
    return "neutral";
  }
  if (status === "FAILED_VALIDATION" || status === "WAITING_REVIEW" || status === "RETRIED") {
    return "warn";
  }
  return "bad";
}
</script>

<template>
  <div class="workflow-steps">
    <article v-for="step in steps" :key="`${prefix}-${step.stepIndex}`" class="workflow-step">
      <strong>#{{ step.stepIndex }} · {{ step.stepName }}</strong>
      <span class="status-pill" :class="tone(step.status)">{{ step.status }}</span>
      <p v-if="step.errorMessage" class="message error">{{ step.errorMessage }}</p>
      <button
        v-if="retryable"
        class="button ghost compact-button"
        type="button"
        @click="$emit('retry-step', step.stepIndex)"
      >
        重试记录
      </button>
    </article>
  </div>
</template>
