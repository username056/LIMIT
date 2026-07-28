<script setup>
defineProps({
  variant: {
    type: String,
    default: 'primary', // 'primary' | 'gray' | 'success' | 'danger'
  },
})
</script>

<template>
  <span
    class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold"
    :class="[
      variant === 'primary' && 'bg-accent text-primary-dark',
      variant === 'gray' && 'bg-bg text-text-sub',
      variant === 'success' && 'bg-green-50 text-green-600',
      variant === 'danger' && 'bg-red-50 text-red-600',
    ]"
  >
    <slot />
  </span>
</template>
