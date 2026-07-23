<script setup>
defineProps({
  label: { type: String, default: '' },
  modelValue: { type: String, default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  autocomplete: { type: String, default: '' },
  required: { type: Boolean, default: false },
  error: { type: String, default: '' }, // 에러 메시지가 있으면 빨간 테두리 + 아래에 메시지 표시
})
defineEmits(['update:modelValue'])
</script>

<template>
  <label class="block">
    <span
      v-if="label"
      class="mb-2 block text-sm font-medium text-text-main"
    >{{ label }}</span>
    <input
      :type="type"
      :value="modelValue"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      :required="required"
      class="w-full rounded-md border bg-surface px-4 py-3 text-sm text-text-main placeholder:text-text-sub outline-none transition-colors"
      :class="error ? 'border-red-400 focus:border-red-500' : 'border-border focus:border-primary'"
      @input="$emit('update:modelValue', $event.target.value)"
    >
    <span
      v-if="error"
      class="mt-1 block text-xs text-red-500"
    >{{ error }}</span>
  </label>
</template>
