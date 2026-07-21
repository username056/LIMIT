<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary', // 'primary' | 'outline' | 'ghost'
  },
  block: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <button
    class="inline-flex items-center justify-center gap-2 rounded-md px-5 py-3 text-sm font-semibold transition-all active:scale-[0.98]"
    :class="[
      block ? 'w-full' : '',
      variant === 'primary' && 'bg-primary-gradient text-white shadow-elevated hover:brightness-105',
      variant === 'outline' && 'border border-border bg-surface text-text-main hover:border-primary hover:text-primary',
      variant === 'ghost' && 'text-text-sub hover:text-text-main',
    ]"
  >
    <slot />
  </button>
</template>
