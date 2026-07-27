<script setup>
import { computed } from 'vue'
import { RouterLink } from 'vue-router'

defineOptions({ inheritAttrs: false })

const props = defineProps({
  variant: {
    type: String,
    default: 'primary', // 'primary' | 'outline' | 'ghost'
  },
  block: {
    type: Boolean,
    default: false,
  },
  to: {
    type: [String, Object],
    default: '',
  },
  type: {
    type: String,
    default: 'button',
  },
  disabled: {
    type: Boolean,
    default: false,
  },
})

const componentTag = computed(() => props.to && !props.disabled ? RouterLink : 'button')
</script>

<template>
  <component
    :is="componentTag"
    v-bind="$attrs"
    :to="to && !disabled ? to : undefined"
    :type="to && !disabled ? undefined : type"
    :disabled="disabled"
    :aria-disabled="disabled || undefined"
    class="inline-flex items-center justify-center gap-2 rounded-md px-5 py-3 text-[15px] font-semibold transition-all active:scale-[0.98]"
    :class="[
      block ? 'w-full' : '',
      disabled ? 'cursor-not-allowed opacity-55 active:scale-100' : '',
      variant === 'primary' && 'bg-primary-gradient text-white shadow-elevated hover:brightness-105',
      variant === 'outline' && 'border border-border bg-surface text-text-main hover:border-primary hover:text-primary',
      variant === 'ghost' && 'text-text-sub hover:text-text-main',
    ]"
  >
    <slot />
  </component>
</template>
