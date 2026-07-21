<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: String, required: true },
  trend: { type: String, default: '' }, // 예: '전월 대비 +12%'
  icon: { type: String, default: '' },
})
</script>

<template>
  <div class="rounded-lg border border-border bg-surface p-5 shadow-card">
    <div class="mb-2 flex items-center justify-between">
      <p class="text-xs text-text-sub">
        {{ label }}
      </p>
      <span
        v-if="icon"
        class="text-sm"
      >{{ icon }}</span>
    </div>
    <p class="text-2xl font-bold text-text-main">
      {{ value }}
    </p>
    <p
      v-if="trend"
      class="mt-1 text-xs text-primary"
    >
      {{ trend }}
    </p>
  </div>
</template>
