<script setup>
defineProps({
  steps: { type: Array, required: true }, // [{ label: '정보 입력', description: '...' }]
  currentStep: { type: Number, required: true }, // 1부터 시작
})
</script>

<template>
  <div class="flex gap-4">
    <div
      v-for="(step, idx) in steps"
      :key="step.label"
      class="flex-1 rounded-lg border p-4"
      :class="idx + 1 === currentStep ? 'border-primary bg-accent' : 'border-border bg-surface'"
    >
      <span
        class="mb-2 inline-flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold"
        :class="idx + 1 === currentStep ? 'bg-primary-gradient text-white' : 'bg-bg text-text-sub'"
      >
        {{ idx + 1 }}
      </span>
      <p class="text-sm font-semibold text-text-main">
        {{ step.label }}
      </p>
      <p
        v-if="step.description"
        class="mt-1 text-xs text-text-sub"
      >
        {{ step.description }}
      </p>
    </div>
  </div>
</template>
