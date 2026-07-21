<script setup>
defineProps({
  label: { type: String, default: '' },
  startDate: { type: String, default: '' },
  endDate: { type: String, default: '' },
})
defineEmits(['update:startDate', 'update:endDate'])
</script>

<template>
  <label class="block">
    <span
      v-if="label"
      class="mb-2 block text-sm font-medium text-text-main"
    >{{ label }}</span>
    <div class="flex items-center gap-2 rounded-md border border-border bg-surface px-3 py-2">
      <input
        type="date"
        :value="startDate"
        class="w-full bg-transparent text-sm text-text-main outline-none"
        @input="$emit('update:startDate', $event.target.value)"
      >
      <span class="text-text-sub">~</span>
      <input
        type="date"
        :value="endDate"
        class="w-full bg-transparent text-sm text-text-main outline-none"
        @input="$emit('update:endDate', $event.target.value)"
      >
    </div>
  </label>
</template>
