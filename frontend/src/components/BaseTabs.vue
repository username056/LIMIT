<script setup>
defineProps({
  tabs: { type: Array, required: true }, // ['전체', '결제완료', '배송중']
  modelValue: { type: String, required: true },
})
defineEmits(['update:modelValue'])
</script>

<template>
  <div class="flex gap-1 border-b border-border">
    <button
      v-for="tab in tabs"
      :key="tab"
      class="-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors"
      :class="modelValue === tab
        ? 'border-primary text-primary'
        : 'border-transparent text-text-sub hover:text-text-main'"
      @click="$emit('update:modelValue', tab)"
    >
      {{ tab }}
    </button>
  </div>
</template>
