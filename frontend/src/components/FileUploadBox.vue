<script setup>
defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  accept: { type: String, default: 'image/*,.pdf' },
})
const emit = defineEmits(['change'])

function handleChange(e) {
  const file = e.target.files?.[0] || null
  emit('change', file)
}
</script>

<template>
  <label class="flex cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed border-border bg-bg px-4 py-8 text-center transition-colors hover:border-primary">
    <svg
      class="mb-2 h-6 w-6 text-text-sub"
      fill="none"
      viewBox="0 0 24 24"
      stroke="currentColor"
    >
      <path
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="2"
        d="M12 16V4m0 0L8 8m4-4l4 4M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2"
      />
    </svg>
    <p class="text-sm font-semibold text-text-main">{{ title }}</p>
    <p
      v-if="description"
      class="mt-1 text-xs text-text-sub"
    >{{ description }}</p>
    <input
      type="file"
      :accept="accept"
      class="hidden"
      @change="handleChange"
    >
  </label>
</template>
