<script setup>
defineProps({
  padded: { type: Boolean, default: true },
})
</script>

<template>
  <div
    class="rounded-lg border border-border bg-surface shadow-card"
    :class="padded ? 'p-6' : ''"
  >
    <slot />
  </div>
</template>
