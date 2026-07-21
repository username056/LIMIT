<script setup>
defineProps({
  label: { type: String, default: '' },
  modelValue: { type: String, default: '' },
  options: { type: Array, required: true }, // ['국민은행', '신한은행'] 또는 [{label, value}]
  placeholder: { type: String, default: '선택하세요' },
})
defineEmits(['update:modelValue'])
</script>

<template>
  <label class="block">
    <span
      v-if="label"
      class="mb-2 block text-sm font-medium text-text-main"
    >{{ label }}</span>
    <select
      :value="modelValue"
      class="w-full rounded-md border border-border bg-surface px-4 py-3 text-sm text-text-main outline-none focus:border-primary"
      @change="$emit('update:modelValue', $event.target.value)"
    >
      <option
        value=""
        disabled
      >{{ placeholder }}</option>
      <option
        v-for="opt in options"
        :key="typeof opt === 'string' ? opt : opt.value"
        :value="typeof opt === 'string' ? opt : opt.value"
      >
        {{ typeof opt === 'string' ? opt : opt.label }}
      </option>
    </select>
  </label>
</template>
